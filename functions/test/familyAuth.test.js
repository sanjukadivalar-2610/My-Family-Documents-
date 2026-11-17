"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createFamilyAuthService, HttpError } = require("../lib/familyAuthCore");

function createMockBackend() {
  const store = new Map();
  const authUsers = new Map();
  const revokedTokens = new Set();

  const clone = (val) => (val === undefined ? undefined : JSON.parse(JSON.stringify(val)));

  const adapter = {
    verifyIdToken: async (token) => {
      if (!token.startsWith("valid_token:")) {
        throw new Error("invalid token");
      }
      const uid = token.slice("valid_token:".length);
      return { uid };
    },
    createCustomToken: async (uid, claims) => {
      return `custom_jwt_${uid}_${claims.familyId}_${claims.authType}`;
    },
    ensureAuthUser: async (uid, displayName) => {
      authUsers.set(uid, { uid, displayName });
    },
    revokeRefreshTokens: async (uid) => {
      revokedTokens.add(uid);
    },
    runTransaction: async (fn) => {
      const tx = {
        get: async (path) => ({
          exists: store.has(path),
          data: () => clone(store.get(path)),
        }),
        set: (path, data) => store.set(path, clone(data)),
        update: (path, data) => {
          if (!store.has(path)) throw new Error(`Missing doc ${path}`);
          store.set(path, { ...store.get(path), ...clone(data) });
        },
        delete: (path) => store.delete(path),
      };
      return fn(tx);
    },
    getDoc: async (path) => ({
      exists: store.has(path),
      data: () => clone(store.get(path)),
    }),
    setDoc: async (path, data) => store.set(path, clone(data)),
    updateDoc: async (path, data) => {
      if (!store.has(path)) throw new Error(`Missing doc ${path}`);
      store.set(path, { ...store.get(path), ...clone(data) });
    },
    deleteDoc: async (path) => store.delete(path),
    listDocsWhereEqual: async (colPath, field, val) => {
      const prefix = `${colPath}/`;
      const out = [];
      for (const [k, v] of store.entries()) {
        if (k.startsWith(prefix) && !k.slice(prefix.length).includes("/") && v[field] === val) {
          out.push({ id: k.slice(prefix.length), data: clone(v) });
        }
      }
      return out;
    },
    listDocsWhereArrayContains: async (colPath, field, val) => {
      const prefix = `${colPath}/`;
      const out = [];
      for (const [k, v] of store.entries()) {
        if (
          k.startsWith(prefix) &&
          !k.slice(prefix.length).includes("/") &&
          Array.isArray(v[field]) &&
          v[field].includes(val)
        ) {
          out.push({ id: k.slice(prefix.length), data: clone(v) });
        }
      }
      return out;
    },
    serverTimestamp: () => "SERVER_TS",
  };

  const seedFamily = (familyId = "fam_alpha", adminUid = "admin_uid_1") => {
    store.set(`families/${familyId}`, {
      familyId,
      familyName: "Rao Family",
      familyCode: "MFD-1020-RA",
      adminUid,
      memberUids: [adminUid],
      totalDocuments: 2,
      autoBackupEnabled: true,
    });
    store.set(`families/${familyId}/members/${adminUid}`, {
      familyId,
      name: "Ananya Rao",
      relation: "Self",
      role: "admin",
      linkedUserId: adminUid,
      email: "ananya@gmail.com",
      memberUids: [adminUid],
      adminUid,
    });
    store.set(`families/${familyId}/documents/doc_shared`, {
      familyId,
      title: "Family Ration Card",
      allowedUids: [adminUid],
      adminUid,
    });
  };

  return {
    store,
    authUsers,
    revokedTokens,
    service: createFamilyAuthService(adapter),
    seedFamily,
  };
}

test("Admin sets salted scrypt Family Username & Password; non-admin rejected", async () => {
  const { store, service, seedFamily } = createMockBackend();
  seedFamily("fam_1", "admin_1");

  await assert.rejects(
    () =>
      service.upsertFamilyCredentials({
        idToken: "valid_token:outsider_99",
        familyId: "fam_1",
        username: "raovault",
        password: "VaultPassword123",
      }),
    (err) => err instanceof HttpError && err.status === 403
  );

  const res = await service.upsertFamilyCredentials({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    username: "RaoVault_01",
    password: "VaultPassword123",
  });

  assert.equal(res.familyUsername, "raovault_01");
  const storedCred = store.get("family_credentials/raovault_01");
  assert.ok(storedCred);
  assert.ok(storedCred.passwordSalt);
  assert.ok(storedCred.passwordHash);
  assert.notEqual(storedCred.passwordHash, "VaultPassword123");
  assert.equal(JSON.stringify(storedCred).includes("VaultPassword123"), false);

  const fam = store.get("families/fam_1");
  assert.equal(fam.familyUsername, "raovault_01");
  assert.equal(fam.hasFamilyPassword, true);
  assert.equal(fam.passwordHash, undefined);
});

test("Two phones logging in with Family Username receive distinct individual UIDs & shared vault key", async () => {
  const { store, service, seedFamily } = createMockBackend();
  seedFamily("fam_1", "admin_1");

  await service.upsertFamilyCredentials({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    username: "sharma_family",
    password: "SecretVault99",
  });

  const phone2 = await service.loginWithFamilyUsername({
    username: "sharma_family",
    password: "SecretVault99",
    deviceId: "device-pixel-phone-2",
    memberName: "Kiran",
    clientIp: "10.0.0.2",
  });

  const phone3 = await service.loginWithFamilyUsername({
    username: "sharma_family",
    password: "SecretVault99",
    deviceId: "device-galaxy-phone-3",
    memberName: "Meera",
    clientIp: "10.0.0.3",
  });

  assert.notEqual(phone2.uid, "admin_1");
  assert.notEqual(phone3.uid, "admin_1");
  assert.notEqual(phone2.uid, phone3.uid);
  assert.ok(phone2.customToken.includes(phone2.uid));
  assert.equal(phone2.vaultKeyBase64, phone3.vaultKeyBase64);
  assert.equal(Buffer.from(phone2.vaultKeyBase64, "base64").length, 32);

  const fam = store.get("families/fam_1");
  assert.ok(fam.memberUids.includes(phone2.uid));
  assert.ok(fam.memberUids.includes(phone3.uid));

  const sharedDoc = store.get("families/fam_1/documents/doc_shared");
  assert.ok(sharedDoc.allowedUids.includes(phone2.uid));
  assert.ok(sharedDoc.allowedUids.includes(phone3.uid));
});

test("Wrong password & unknown username produce identical error and lock after 5 attempts", async () => {
  const { service, seedFamily } = createMockBackend();
  seedFamily("fam_1", "admin_1");

  await service.upsertFamilyCredentials({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    username: "kulkarni_home",
    password: "CorrectPassword1",
  });

  let unknownErr;
  try {
    await service.loginWithFamilyUsername({
      username: "nonexistent_user",
      password: "SomePassword1",
      deviceId: "device-test-1234",
      clientIp: "1.2.3.4",
    });
  } catch (e) {
    unknownErr = e;
  }

  let wrongPwdErr;
  for (let i = 0; i < 4; i++) {
    try {
      await service.loginWithFamilyUsername({
        username: "kulkarni_home",
        password: "WrongPassword9",
        deviceId: "device-test-1234",
        clientIp: "1.2.3.4",
      });
    } catch (e) {
      wrongPwdErr = e;
    }
  }

  assert.equal(unknownErr.status, 401);
  assert.equal(wrongPwdErr.status, 401);
  assert.equal(unknownErr.message, wrongPwdErr.message);

  // 5th attempt triggers lockout even before or on 6th attempt
  await assert.rejects(
    () =>
      service.loginWithFamilyUsername({
        username: "kulkarni_home",
        password: "WrongPassword9",
        deviceId: "device-test-1234",
        clientIp: "1.2.3.4",
      }),
    (err) => err instanceof HttpError && err.status === 401
  );

  // Subsequent attempt with EVEN CORRECT password is blocked with 429 until lock expires
  await assert.rejects(
    () =>
      service.loginWithFamilyUsername({
        username: "kulkarni_home",
        password: "CorrectPassword1",
        deviceId: "device-test-1234",
        clientIp: "1.2.3.4",
      }),
    (err) => err instanceof HttpError && err.status === 429
  );
});

test("Admin can create individual member login, change family username/password, and revoke member", async () => {
  const { store, revokedTokens, service, seedFamily } = createMockBackend();
  seedFamily("fam_1", "admin_1");

  await service.upsertFamilyCredentials({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    username: "old_family_name",
    password: "OldPassword123",
  });

  // Change username & password
  await service.upsertFamilyCredentials({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    username: "new_family_name",
    password: "NewPassword123",
  });

  await assert.rejects(
    () =>
      service.loginWithFamilyUsername({
        username: "old_family_name",
        password: "OldPassword123",
        deviceId: "device-abc-12345",
        clientIp: "9.9.9.9",
      }),
    (err) => err instanceof HttpError && err.status === 401
  );

  // Create individual member login
  const memberCreated = await service.createMemberCredential({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    memberName: "Rohan Rao",
    relation: "Son",
    memberUsername: "rohan.rao",
    password: "RohanPass123",
  });

  const memberLogin = await service.loginWithFamilyUsername({
    username: "rohan.rao",
    password: "RohanPass123",
    deviceId: "device-rohan-001",
    clientIp: "8.8.8.8",
  });
  assert.equal(memberLogin.uid, memberCreated.memberUid);
  assert.equal(memberLogin.authMethod, "member_username");

  // Revoke member access
  await service.revokeMemberAccess({
    idToken: "valid_token:admin_1",
    familyId: "fam_1",
    memberUsername: "rohan.rao",
    memberUid: memberCreated.memberUid,
  });

  assert.ok(revokedTokens.has(memberCreated.memberUid));
  const famAfter = store.get("families/fam_1");
  assert.equal(famAfter.memberUids.includes(memberCreated.memberUid), false);
  const docAfter = store.get("families/fam_1/documents/doc_shared");
  assert.equal(docAfter.allowedUids.includes(memberCreated.memberUid), false);

  await assert.rejects(
    () =>
      service.loginWithFamilyUsername({
        username: "rohan.rao",
        password: "RohanPass123",
        deviceId: "device-rohan-001",
        clientIp: "8.8.8.8",
      }),
    (err) => err instanceof HttpError && err.status === 401
  );
});
