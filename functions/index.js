"use strict";

const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { HttpError, createFamilyAuthService } = require("./lib/familyAuthCore");

const FIRESTORE_DATABASE_ID =
  "ai-studio-android-myfamily-31bcdd9d-0dee-46fe-8aa9-4f2cc590f319";

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = getFirestore(admin.app(), FIRESTORE_DATABASE_ID);
const auth = admin.auth();

const firestoreAdapter = {
  verifyIdToken: (idToken) => auth.verifyIdToken(idToken, true),
  createCustomToken: (uid, claims) => auth.createCustomToken(uid, claims),
  ensureAuthUser: async (uid, displayName) => {
    try {
      await auth.getUser(uid);
      if (displayName) {
        await auth.updateUser(uid, { displayName, disabled: false });
      }
    } catch (err) {
      if (err && err.code === "auth/user-not-found") {
        await auth.createUser({ uid, displayName });
      } else {
        throw err;
      }
    }
  },
  revokeRefreshTokens: async (uid) => {
    try {
      await auth.revokeRefreshTokens(uid);
    } catch (_err) {
      // Ignore if auth record wasn't initialized yet
    }
  },
  runTransaction: (fn) =>
    db.runTransaction(async (tx) => {
      const txWrapper = {
        get: (docPath) => tx.get(db.doc(docPath)),
        set: (docPath, data) => tx.set(db.doc(docPath), data),
        update: (docPath, data) => tx.update(db.doc(docPath), data),
        delete: (docPath) => tx.delete(db.doc(docPath)),
      };
      return fn(txWrapper);
    }),
  getDoc: (docPath) => db.doc(docPath).get(),
  setDoc: (docPath, data) => db.doc(docPath).set(data),
  updateDoc: (docPath, data) => db.doc(docPath).update(data),
  deleteDoc: (docPath) => db.doc(docPath).delete(),
  listDocsWhereEqual: async (colPath, field, val) => {
    const snap = await db.collection(colPath).where(field, "==", val).get();
    return snap.docs.map((d) => ({ id: d.id, data: d.data() }));
  },
  listDocsWhereArrayContains: async (colPath, field, val) => {
    const snap = await db.collection(colPath).where(field, "array-contains", val).get();
    return snap.docs.map((d) => ({ id: d.id, data: d.data() }));
  },
  serverTimestamp: () => FieldValue.serverTimestamp(),
};

const service = createFamilyAuthService(firestoreAdapter);

function extractBearerToken(req) {
  const header = req.get("Authorization") || "";
  if (header.startsWith("Bearer ")) {
    return header.slice("Bearer ".length).trim();
  }
  return "";
}

function extractClientIp(req) {
  const xff = req.get("x-forwarded-for") || "";
  if (xff) return xff.split(",")[0].trim();
  return req.ip || "unknown";
}

exports.familyAuth = functions.region("us-central1").https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed", code: "METHOD_NOT_ALLOWED" });
    return;
  }

  try {
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const action = String(body.action || "").trim();
    const idToken = extractBearerToken(req);

    let result;
    switch (action) {
      case "upsertFamilyCredentials":
        result = await service.upsertFamilyCredentials({
          idToken,
          familyId: body.familyId,
          username: body.username,
          password: body.password,
        });
        break;
      case "resetFamilyPassword":
        result = await service.resetFamilyPassword({
          idToken,
          familyId: body.familyId,
          newPassword: body.newPassword,
        });
        break;
      case "createMemberCredential":
        result = await service.createMemberCredential({
          idToken,
          familyId: body.familyId,
          memberName: body.memberName,
          relation: body.relation,
          memberUsername: body.memberUsername,
          password: body.password,
          existingMemberId: body.existingMemberId,
        });
        break;
      case "resetMemberPassword":
        result = await service.resetMemberPassword({
          idToken,
          familyId: body.familyId,
          memberUsername: body.memberUsername,
          newPassword: body.newPassword,
        });
        break;
      case "revokeMemberAccess":
        result = await service.revokeMemberAccess({
          idToken,
          familyId: body.familyId,
          memberUsername: body.memberUsername,
          memberUid: body.memberUid,
        });
        break;
      case "listFamilyCredentials":
        result = await service.listFamilyCredentials({
          idToken,
          familyId: body.familyId,
        });
        break;
      case "loginWithFamilyUsername":
        result = await service.loginWithFamilyUsername({
          username: body.username,
          password: body.password,
          deviceId: body.deviceId,
          memberName: body.memberName,
          clientIp: extractClientIp(req),
        });
        break;
      default:
        throw new HttpError(400, "UNKNOWN_ACTION", "Unsupported auth action.");
    }

    res.status(200).json(result);
  } catch (err) {
    if (err instanceof HttpError) {
      res.status(err.status).json({ error: err.message, code: err.code });
      return;
    }
    console.error("familyAuth unexpected error:", err);
    res.status(500).json({ error: "Internal server error", code: "INTERNAL_ERROR" });
  }
});
