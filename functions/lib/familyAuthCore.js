"use strict";

const crypto = require("crypto");

const USERNAME_REGEX = /^[a-z0-9][a-z0-9._-]{2,31}$/;
const MIN_PASSWORD_LEN = 8;
const MAX_PASSWORD_LEN = 128;
const MAX_FAILED_ATTEMPTS = 5;
const LOCK_WINDOW_MS = 15 * 60 * 1000;
const DUMMY_SALT = "0123456789abcdef0123456789abcdef";
const DUMMY_HASH =
  "a".repeat(128);

class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function normalizeUsername(raw) {
  return String(raw || "").trim().toLowerCase();
}

function validateUsername(username) {
  if (!USERNAME_REGEX.test(username)) {
    throw new HttpError(
      400,
      "INVALID_USERNAME",
      "Username must be 3-32 characters (lowercase letters, digits, '.', '_', '-')."
    );
  }
}

function validatePassword(password) {
  const pwd = String(password || "");
  if (pwd.length < MIN_PASSWORD_LEN || pwd.length > MAX_PASSWORD_LEN) {
    throw new HttpError(
      400,
      "WEAK_PASSWORD",
      `Password must be between ${MIN_PASSWORD_LEN} and ${MAX_PASSWORD_LEN} characters.`
    );
  }
  const hasLetter = /[A-Za-z]/.test(pwd);
  const hasDigit = /[0-9]/.test(pwd);
  if (!hasLetter || !hasDigit) {
    throw new HttpError(
      400,
      "WEAK_PASSWORD",
      "Password must include at least one letter and one number."
    );
  }
}

function hashPasswordScrypt(password, saltHex) {
  const salt = saltHex || crypto.randomBytes(16).toString("hex");
  const derived = crypto.scryptSync(String(password), salt, 64, {
    N: 16384,
    r: 8,
    p: 1,
  });
  return {
    salt,
    hash: derived.toString("hex"),
  };
}

function verifyPasswordConstantTime(password, saltHex, expectedHashHex) {
  const effectiveSalt = saltHex || DUMMY_SALT;
  const expectedBuf = Buffer.from(
    expectedHashHex && expectedHashHex.length === 128 ? expectedHashHex : DUMMY_HASH,
    "hex"
  );
  const candidateBuf = crypto.scryptSync(String(password || ""), effectiveSalt, 64, {
    N: 16384,
    r: 8,
    p: 1,
  });
  if (candidateBuf.length !== expectedBuf.length) {
    return false;
  }
  const matched = crypto.timingSafeEqual(candidateBuf, expectedBuf);
  return Boolean(saltHex && expectedHashHex && matched);
}

function sha256Hex(input) {
  return crypto.createHash("sha256").update(String(input)).digest("hex");
}

function generateRandomVaultKeyBase64() {
  return crypto.randomBytes(32).toString("base64");
}

function sanitizeDisplayName(name, fallback = "Family Member") {
  const clean = String(name || "").trim().slice(0, 80);
  return clean.length > 0 ? clean : fallback;
}

function sanitizeRelation(relation) {
  const clean = String(relation || "").trim().slice(0, 40);
  return clean.length > 0 ? clean : "Member";
}

function buildRateLimitKey(usernameLower, clientIp) {
  return sha256Hex(`lock|${usernameLower}|${String(clientIp || "unknown").slice(0, 64)}`);
}

/**
 * Pure service factory backed by an adapter object:
 * - verifyIdToken(idToken) -> { uid, email, name }
 * - createCustomToken(uid, claims) -> string
 * - ensureAuthUser(uid, displayName) -> Promise<void>
 * - revokeRefreshTokens(uid) -> Promise<void>
 * - runTransaction(fn) -> Promise<T>
 * - col(name) / docRef helpers via adapter:
 *   dbGet(path), dbSet(path, data, merge?), dbUpdate(path, data), dbDelete(path),
 *   listDocsWhereArrayContains(colPath, field, val), listDocsWhereEqual(colPath, field, val),
 *   nowTimestamp(), arrayUnion(val), arrayRemove(val), serverTimestamp()
 */
function createFamilyAuthService(adapter) {
  async function requireVerifiedAdmin(idToken, familyId) {
    if (!idToken) {
      throw new HttpError(401, "UNAUTHENTICATED", "Admin authentication token is required.");
    }
    let decoded;
    try {
      decoded = await adapter.verifyIdToken(idToken);
    } catch (_err) {
      throw new HttpError(401, "UNAUTHENTICATED", "Invalid or expired Firebase ID token.");
    }
    if (!familyId || !/^[a-zA-Z0-9_-]{4,128}$/.test(familyId)) {
      throw new HttpError(400, "INVALID_FAMILY_ID", "Valid familyId is required.");
    }
    const familySnap = await adapter.getDoc(`families/${familyId}`);
    if (!familySnap.exists) {
      throw new HttpError(404, "FAMILY_NOT_FOUND", "Family not found.");
    }
    const familyData = familySnap.data();
    if (familyData.adminUid !== decoded.uid) {
      throw new HttpError(403, "FORBIDDEN", "Only the Family Admin can perform this action.");
    }
    return { callerUid: decoded.uid, familyData };
  }

  async function ensureFamilyVaultKey(familyId, adminUid) {
    const secretPath = `families/${familyId}/secrets/vault_key`;
    const secretSnap = await adapter.getDoc(secretPath);
    if (secretSnap.exists && secretSnap.data().keyBase64) {
      return secretSnap.data().keyBase64;
    }
    const keyBase64 = generateRandomVaultKeyBase64();
    await adapter.setDoc(secretPath, {
      familyId,
      adminUid,
      keyBase64,
      keyVersion: 1,
      createdAt: adapter.serverTimestamp(),
      updatedAt: adapter.serverTimestamp(),
    });
    return keyBase64;
  }

  async function upsertFamilyCredentials({ idToken, familyId, username, password }) {
    const { callerUid, familyData } = await requireVerifiedAdmin(idToken, familyId);
    const usernameLower = normalizeUsername(username);
    validateUsername(usernameLower);
    validatePassword(password);

    const { salt, hash } = hashPasswordScrypt(password);
    const oldUsername = familyData.familyUsername
      ? normalizeUsername(familyData.familyUsername)
      : null;

    await adapter.runTransaction(async (tx) => {
      const famPath = `families/${familyId}`;
      const famSnap = await tx.get(famPath);
      if (!famSnap.exists) {
        throw new HttpError(404, "FAMILY_NOT_FOUND", "Family not found.");
      }
      const fam = famSnap.data();
      if (fam.adminUid !== callerUid) {
        throw new HttpError(403, "FORBIDDEN", "Only the Family Admin can update credentials.");
      }

      const newCredPath = `family_credentials/${usernameLower}`;
      const targetCredSnap = await tx.get(newCredPath);
      if (targetCredSnap.exists) {
        const existingCred = targetCredSnap.data();
        if (existingCred.familyId !== familyId || existingCred.type !== "family") {
          throw new HttpError(
            409,
            "USERNAME_TAKEN",
            "This username is already taken. Choose another unique username."
          );
        }
      }

      const prevVersion = targetCredSnap.exists ? Number(targetCredSnap.data().passwordVersion || 0) : 0;

      if (oldUsername && oldUsername !== usernameLower) {
        const oldCredPath = `family_credentials/${oldUsername}`;
        const oldSnap = await tx.get(oldCredPath);
        if (oldSnap.exists && oldSnap.data().familyId === familyId && oldSnap.data().type === "family") {
          tx.delete(oldCredPath);
        }
      }

      tx.set(newCredPath, {
        username: usernameLower,
        type: "family",
        familyId,
        adminUid: callerUid,
        memberId: "",
        memberName: fam.familyName || "Family",
        linkedUid: "",
        passwordSalt: salt,
        passwordHash: hash,
        passwordVersion: prevVersion + 1,
        active: true,
        updatedAt: adapter.serverTimestamp(),
        createdAt: targetCredSnap.exists ? targetCredSnap.data().createdAt : adapter.serverTimestamp(),
      });

      tx.update(famPath, {
        familyUsername: usernameLower,
        hasFamilyPassword: true,
        updatedAt: adapter.serverTimestamp(),
      });
    });

    await ensureFamilyVaultKey(familyId, callerUid);

    return {
      ok: true,
      familyId,
      familyUsername: usernameLower,
    };
  }

  async function resetFamilyPassword({ idToken, familyId, newPassword }) {
    const { callerUid, familyData } = await requireVerifiedAdmin(idToken, familyId);
    const usernameLower = normalizeUsername(familyData.familyUsername);
    if (!usernameLower) {
      throw new HttpError(
        400,
        "CREDENTIALS_NOT_SET",
        "Set a Family Username first before resetting the password."
      );
    }
    return upsertFamilyCredentials({
      idToken,
      familyId,
      username: usernameLower,
      password: newPassword,
    });
  }

  async function createMemberCredential({
    idToken,
    familyId,
    memberName,
    relation,
    memberUsername,
    password,
    existingMemberId,
  }) {
    const { callerUid, familyData } = await requireVerifiedAdmin(idToken, familyId);
    const usernameLower = normalizeUsername(memberUsername);
    validateUsername(usernameLower);
    validatePassword(password);

    const cleanName = sanitizeDisplayName(memberName, "Family Member");
    const cleanRelation = sanitizeRelation(relation);
    const memberUid = `fmem_${sha256Hex(`${familyId}|${usernameLower}`).slice(0, 20)}`;
    const memberDocId = existingMemberId && /^[a-zA-Z0-9_-]{3,128}$/.test(existingMemberId)
      ? existingMemberId
      : memberUid;

    await adapter.ensureAuthUser(memberUid, cleanName);
    const { salt, hash } = hashPasswordScrypt(password);

    await adapter.runTransaction(async (tx) => {
      const credPath = `family_credentials/${usernameLower}`;
      const existingCredSnap = await tx.get(credPath);
      if (existingCredSnap.exists) {
        const prev = existingCredSnap.data();
        if (prev.familyId !== familyId || prev.type !== "member" || prev.linkedUid !== memberUid) {
          throw new HttpError(
            409,
            "USERNAME_TAKEN",
            "This username is already taken. Choose another unique username."
          );
        }
      }

      const famPath = `families/${familyId}`;
      const famSnap = await tx.get(famPath);
      const fam = famSnap.data();
      const currentMembers = Array.isArray(fam.memberUids) ? fam.memberUids : [callerUid];
      if (!currentMembers.includes(memberUid) && currentMembers.length >= 25) {
        throw new HttpError(400, "MEMBER_LIMIT_REACHED", "Maximum family member limit (25) reached.");
      }
      const nextMemberUids = Array.from(new Set([...currentMembers, memberUid]));
      const prevVersion = existingCredSnap.exists
        ? Number(existingCredSnap.data().passwordVersion || 0)
        : 0;

      tx.set(credPath, {
        username: usernameLower,
        type: "member",
        familyId,
        adminUid: callerUid,
        memberId: memberDocId,
        memberName: cleanName,
        relation: cleanRelation,
        linkedUid: memberUid,
        passwordSalt: salt,
        passwordHash: hash,
        passwordVersion: prevVersion + 1,
        active: true,
        updatedAt: adapter.serverTimestamp(),
        createdAt: existingCredSnap.exists ? existingCredSnap.data().createdAt : adapter.serverTimestamp(),
      });

      tx.update(famPath, {
        memberUids: nextMemberUids,
        updatedAt: adapter.serverTimestamp(),
      });

      const memPath = `families/${familyId}/members/${memberDocId}`;
      const memSnap = await tx.get(memPath);
      if (memSnap.exists) {
        tx.update(memPath, {
          name: cleanName,
          relation: cleanRelation,
          linkedUserId: memberUid,
          memberUids: nextMemberUids,
          memberUsername: usernameLower,
          loginEnabled: true,
          updatedAt: adapter.serverTimestamp(),
        });
      } else {
        tx.set(memPath, {
          familyId,
          name: cleanName,
          relation: cleanRelation,
          role: "member",
          linkedUserId: memberUid,
          email: "",
          memberUids: nextMemberUids,
          adminUid: callerUid,
          memberUsername: usernameLower,
          loginEnabled: true,
          createdAt: adapter.serverTimestamp(),
          updatedAt: adapter.serverTimestamp(),
        });
      }

      const userPath = `users/${memberUid}`;
      const userSnap = await tx.get(userPath);
      if (userSnap.exists) {
        tx.update(userPath, {
          displayName: cleanName,
          familyId,
          username: usernameLower,
          authMethod: "member_username",
          updatedAt: adapter.serverTimestamp(),
        });
      } else {
        tx.set(userPath, {
          userId: memberUid,
          displayName: cleanName,
          email: "",
          familyId,
          preferredLanguage: "en",
          username: usernameLower,
          authMethod: "member_username",
          createdAt: adapter.serverTimestamp(),
          updatedAt: adapter.serverTimestamp(),
        });
      }
    });

    await ensureFamilyVaultKey(familyId, callerUid);
    await syncExistingFamilyMembersAndSharedDocs(familyId, callerUid, memberUid, familyData.memberUids || [callerUid]);

    return {
      ok: true,
      familyId,
      memberId: memberDocId,
      memberUid,
      memberUsername: usernameLower,
    };
  }

  async function resetMemberPassword({ idToken, familyId, memberUsername, newPassword }) {
    const { callerUid } = await requireVerifiedAdmin(idToken, familyId);
    const usernameLower = normalizeUsername(memberUsername);
    validateUsername(usernameLower);
    validatePassword(newPassword);

    const credPath = `family_credentials/${usernameLower}`;
    const credSnap = await adapter.getDoc(credPath);
    if (!credSnap.exists) {
      throw new HttpError(404, "CREDENTIAL_NOT_FOUND", "Member login not found.");
    }
    const cred = credSnap.data();
    if (cred.familyId !== familyId || cred.adminUid !== callerUid || cred.type !== "member") {
      throw new HttpError(403, "FORBIDDEN", "Not authorized to update this member credential.");
    }
    const { salt, hash } = hashPasswordScrypt(newPassword);
    await adapter.updateDoc(credPath, {
      passwordSalt: salt,
      passwordHash: hash,
      passwordVersion: Number(cred.passwordVersion || 0) + 1,
      active: true,
      updatedAt: adapter.serverTimestamp(),
    });
    return { ok: true, memberUsername: usernameLower };
  }

  async function revokeMemberAccess({ idToken, familyId, memberUsername, memberUid }) {
    const { callerUid } = await requireVerifiedAdmin(idToken, familyId);
    let targetUid = memberUid || "";
    const usernameLower = memberUsername ? normalizeUsername(memberUsername) : "";

    if (usernameLower) {
      const credSnap = await adapter.getDoc(`family_credentials/${usernameLower}`);
      if (credSnap.exists && credSnap.data().familyId === familyId) {
        if (!targetUid) targetUid = credSnap.data().linkedUid || "";
        await adapter.updateDoc(`family_credentials/${usernameLower}`, {
          active: false,
          passwordVersion: Number(credSnap.data().passwordVersion || 0) + 1,
          updatedAt: adapter.serverTimestamp(),
        });
      }
    }

    if (targetUid && targetUid === callerUid) {
      throw new HttpError(400, "CANNOT_REVOKE_ADMIN", "Cannot revoke the Family Admin account.");
    }

    if (targetUid) {
      // Also deactivate any credentials bound to targetUid
      const allCreds = await adapter.listDocsWhereEqual("family_credentials", "linkedUid", targetUid);
      for (const c of allCreds) {
        if (c.data.familyId === familyId) {
          await adapter.updateDoc(`family_credentials/${c.id}`, {
            active: false,
            updatedAt: adapter.serverTimestamp(),
          });
        }
      }

      const famPath = `families/${familyId}`;
      const famSnap = await adapter.getDoc(famPath);
      if (famSnap.exists) {
        const currentUids = Array.isArray(famSnap.data().memberUids) ? famSnap.data().memberUids : [];
        const updatedUids = currentUids.filter((u) => u !== targetUid);
        await adapter.updateDoc(famPath, {
          memberUids: updatedUids,
          updatedAt: adapter.serverTimestamp(),
        });

        const members = await adapter.listDocsWhereEqual(`families/${familyId}/members`, "familyId", familyId);
        for (const m of members) {
          const patch = {
            memberUids: updatedUids,
            updatedAt: adapter.serverTimestamp(),
          };
          if (m.data.linkedUserId === targetUid) {
            patch.loginEnabled = false;
          }
          await adapter.updateDoc(`families/${familyId}/members/${m.id}`, patch);
        }

        const docs = await adapter.listDocsWhereArrayContains(
          `families/${familyId}/documents`,
          "allowedUids",
          targetUid
        );
        for (const d of docs) {
          const allowed = (d.data.allowedUids || []).filter((u) => u !== targetUid);
          if (!allowed.includes(callerUid)) allowed.unshift(callerUid);
          await adapter.updateDoc(`families/${familyId}/documents/${d.id}`, {
            allowedUids: allowed,
            updatedAt: adapter.serverTimestamp(),
          });
        }
      }

      const userSnap = await adapter.getDoc(`users/${targetUid}`);
      if (userSnap.exists && userSnap.data().familyId === familyId) {
        await adapter.updateDoc(`users/${targetUid}`, {
          familyId: "",
          updatedAt: adapter.serverTimestamp(),
        });
      }

      await adapter.revokeRefreshTokens(targetUid);
    }

    return { ok: true, revokedUid: targetUid, familyId };
  }

  async function listFamilyCredentials({ idToken, familyId }) {
    const { familyData } = await requireVerifiedAdmin(idToken, familyId);
    const creds = await adapter.listDocsWhereEqual("family_credentials", "familyId", familyId);
    const memberAccounts = creds
      .filter((c) => c.data.type === "member")
      .map((c) => ({
        username: c.data.username,
        memberId: c.data.memberId || "",
        memberName: c.data.memberName || "",
        relation: c.data.relation || "Member",
        linkedUid: c.data.linkedUid || "",
        active: Boolean(c.data.active),
      }));
    return {
      ok: true,
      familyId,
      familyUsername: familyData.familyUsername || "",
      hasFamilyPassword: Boolean(familyData.hasFamilyPassword),
      memberAccounts,
    };
  }

  async function loginWithFamilyUsername({
    username,
    password,
    deviceId,
    memberName,
    clientIp,
  }) {
    const usernameLower = normalizeUsername(username);
    const pwd = String(password || "");
    if (!usernameLower || !pwd) {
      throw new HttpError(400, "MISSING_FIELDS", "Username and password are required.");
    }

    const lockKey = buildRateLimitKey(usernameLower, clientIp);
    const lockPath = `family_auth_locks/${lockKey}`;
    const lockSnap = await adapter.getDoc(lockPath);
    const nowMs = Date.now();
    const lockData = lockSnap.exists ? lockSnap.data() : null;

    if (lockData && Number(lockData.lockedUntilMs || 0) > nowMs) {
      // Perform dummy work for constant-time behavior before returning 429
      verifyPasswordConstantTime(pwd, null, null);
      throw new HttpError(
        429,
        "TOO_MANY_ATTEMPTS",
        "Too many failed login attempts. Please wait 15 minutes and try again."
      );
    }

    const credSnap = await adapter.getDoc(`family_credentials/${usernameLower}`);
    const cred = credSnap.exists ? credSnap.data() : null;

    const validPassword = cred && cred.active === true
      ? verifyPasswordConstantTime(pwd, cred.passwordSalt, cred.passwordHash)
      : verifyPasswordConstantTime(pwd, null, null);

    if (!validPassword) {
      const withinWindow = lockData && nowMs - Number(lockData.firstFailureAtMs || 0) <= LOCK_WINDOW_MS;
      const nextFailures = withinWindow ? Number(lockData.failedAttempts || 0) + 1 : 1;
      const lockedUntilMs = nextFailures >= MAX_FAILED_ATTEMPTS ? nowMs + LOCK_WINDOW_MS : 0;
      await adapter.setDoc(lockPath, {
        failedAttempts: nextFailures,
        firstFailureAtMs: withinWindow ? lockData.firstFailureAtMs : nowMs,
        lockedUntilMs,
        updatedAtMs: nowMs,
      });
      throw new HttpError(
        401,
        "INVALID_CREDENTIALS",
        "Invalid username or password."
      );
    }

    if (lockSnap.exists) {
      await adapter.deleteDoc(lockPath);
    }

    const familyId = cred.familyId;
    const famPath = `families/${familyId}`;
    const famSnap = await adapter.getDoc(famPath);
    if (!famSnap.exists) {
      throw new HttpError(401, "INVALID_CREDENTIALS", "Invalid username or password.");
    }
    const familyData = famSnap.data();
    const priorMemberUids = Array.isArray(familyData.memberUids) ? [...familyData.memberUids] : [familyData.adminUid];

    let assignedUid = cred.linkedUid;
    let displayName = cred.memberName || sanitizeDisplayName(memberName, "Family Member");

    if (cred.type === "family") {
      // Issue an individual UID per device/installation rather than sharing a single permanent UID
      const cleanDeviceId = String(deviceId || "").trim().slice(0, 128);
      if (cleanDeviceId.length < 8) {
        throw new HttpError(400, "INVALID_DEVICE_ID", "Valid device installation ID is required.");
      }
      const deviceHash = sha256Hex(`${familyId}|${cleanDeviceId}`).slice(0, 20);
      const deviceLoginPath = `families/${familyId}/device_logins/${deviceHash}`;
      const deviceSnap = await adapter.getDoc(deviceLoginPath);

      if (deviceSnap.exists && deviceSnap.data().active === false) {
        throw new HttpError(403, "ACCESS_REVOKED", "This device's family access has been revoked by the Admin.");
      }

      if (deviceSnap.exists && deviceSnap.data().uid) {
        assignedUid = deviceSnap.data().uid;
        displayName = sanitizeDisplayName(memberName, deviceSnap.data().displayName || "Family Member");
      } else {
        assignedUid = `fdev_${deviceHash}`;
        displayName = sanitizeDisplayName(memberName, "Family Member");
        await adapter.setDoc(deviceLoginPath, {
          uid: assignedUid,
          familyId,
          displayName,
          active: true,
          createdAt: adapter.serverTimestamp(),
          updatedAt: adapter.serverTimestamp(),
        });
      }
    }

    await adapter.ensureAuthUser(assignedUid, displayName);

    const isNewFamilyMember = !priorMemberUids.includes(assignedUid);
    if (isNewFamilyMember) {
      if (priorMemberUids.length >= 25) {
        throw new HttpError(400, "MEMBER_LIMIT_REACHED", "Family member limit (25) reached.");
      }
      const nextUids = Array.from(new Set([...priorMemberUids, assignedUid]));
      await adapter.updateDoc(famPath, {
        memberUids: nextUids,
        updatedAt: adapter.serverTimestamp(),
      });
      await syncExistingFamilyMembersAndSharedDocs(familyId, familyData.adminUid, assignedUid, priorMemberUids);

      const memPath = `families/${familyId}/members/${assignedUid}`;
      const memSnap = await adapter.getDoc(memPath);
      if (!memSnap.exists) {
        await adapter.setDoc(memPath, {
          familyId,
          name: displayName,
          relation: cred.relation || "Member",
          role: "member",
          linkedUserId: assignedUid,
          email: "",
          memberUids: nextUids,
          adminUid: familyData.adminUid,
          memberUsername: cred.type === "member" ? usernameLower : "",
          loginEnabled: true,
          createdAt: adapter.serverTimestamp(),
          updatedAt: adapter.serverTimestamp(),
        });
      }
    }

    const userPath = `users/${assignedUid}`;
    const userSnap = await adapter.getDoc(userPath);
    if (!userSnap.exists) {
      await adapter.setDoc(userPath, {
        userId: assignedUid,
        displayName,
        email: "",
        familyId,
        preferredLanguage: "en",
        username: usernameLower,
        authMethod: cred.type === "member" ? "member_username" : "family_username",
        createdAt: adapter.serverTimestamp(),
        updatedAt: adapter.serverTimestamp(),
      });
    } else if (!userSnap.data().familyId) {
      await adapter.updateDoc(userPath, {
        familyId,
        username: usernameLower,
        authMethod: cred.type === "member" ? "member_username" : "family_username",
        updatedAt: adapter.serverTimestamp(),
      });
    }

    const vaultKeyBase64 = await ensureFamilyVaultKey(familyId, familyData.adminUid);
    const customToken = await adapter.createCustomToken(assignedUid, {
      familyId,
      role: "member",
      authType: cred.type,
      pwdVer: Number(cred.passwordVersion || 1),
    });

    return {
      ok: true,
      customToken,
      uid: assignedUid,
      familyId,
      familyName: familyData.familyName || "",
      displayName,
      username: usernameLower,
      authMethod: cred.type === "member" ? "member_username" : "family_username",
      vaultKeyBase64,
    };
  }

  async function syncExistingFamilyMembersAndSharedDocs(
    familyId,
    adminUid,
    newMemberUid,
    priorMemberUids
  ) {
    const nextUids = Array.from(new Set([...priorMemberUids, newMemberUid]));
    const members = await adapter.listDocsWhereEqual(`families/${familyId}/members`, "familyId", familyId);
    for (const m of members) {
      await adapter.updateDoc(`families/${familyId}/members/${m.id}`, {
        memberUids: nextUids,
        updatedAt: adapter.serverTimestamp(),
      });
    }

    // Grant access on documents that were shared with all existing family members
    // while keeping any Admin-restricted documents isolated.
    const docs = await adapter.listDocsWhereArrayContains(
      `families/${familyId}/documents`,
      "allowedUids",
      adminUid
    );
    for (const d of docs) {
      const currentAllowed = Array.isArray(d.data.allowedUids) ? d.data.allowedUids : [adminUid];
      const wasSharedWithAllPrior = priorMemberUids.every((u) => currentAllowed.includes(u));
      if (wasSharedWithAllPrior && !currentAllowed.includes(newMemberUid) && currentAllowed.length < 25) {
        await adapter.updateDoc(`families/${familyId}/documents/${d.id}`, {
          allowedUids: [...currentAllowed, newMemberUid],
          updatedAt: adapter.serverTimestamp(),
        });
      }
    }
  }

  return {
    upsertFamilyCredentials,
    resetFamilyPassword,
    createMemberCredential,
    resetMemberPassword,
    revokeMemberAccess,
    listFamilyCredentials,
    loginWithFamilyUsername,
  };
}

module.exports = {
  HttpError,
  USERNAME_REGEX,
  MAX_FAILED_ATTEMPTS,
  LOCK_WINDOW_MS,
  normalizeUsername,
  validateUsername,
  validatePassword,
  hashPasswordScrypt,
  verifyPasswordConstantTime,
  createFamilyAuthService,
};
