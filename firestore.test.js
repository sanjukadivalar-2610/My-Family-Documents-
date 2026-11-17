const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_alice_123";
const MEMBER_UID = "member_bob_456";
const OUTSIDER_UID = "outsider_eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function serverTime(db) {
  return new Date();
}

async function seedFamilyWithMemberAndDocs() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date();
    await db.collection("families").doc("fam_1").set({
      familyId: "fam_1",
      familyName: "Sharma Family",
      familyCode: "MFD-1020-AB",
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, MEMBER_UID],
      totalDocuments: 2,
      autoBackupEnabled: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("family_invites").doc("MFD-1020-AB").set({
      familyCode: "MFD-1020-AB",
      familyId: "fam_1",
      familyName: "Sharma Family",
      adminUid: ADMIN_UID,
      active: true,
      createdAt: now,
    });
    await db.collection("families").doc("fam_1").collection("members").doc("mem_admin").set({
      familyId: "fam_1",
      name: "Alice Sharma",
      relation: "Self",
      role: "admin",
      linkedUserId: ADMIN_UID,
      email: "alice@test.com",
      memberUids: [ADMIN_UID, MEMBER_UID],
      adminUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_1").collection("documents").doc("doc_shared").set({
      familyId: "fam_1",
      title: "Sample Ration Card",
      category: "Ration Card",
      memberId: "mem_admin",
      memberName: "Alice Sharma",
      uploadedByUid: ADMIN_UID,
      adminUid: ADMIN_UID,
      allowedUids: [ADMIN_UID, MEMBER_UID],
      fileType: "sample",
      fileName: "ration_sample.pdf",
      fileSizeBytes: 1024,
      isEncrypted: true,
      isBackedUp: false,
      isSample: true,
      issueDateMillis: 1700000000000,
      expiryDateMillis: 1800000000000,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").set({
      familyId: "fam_1",
      title: "Admin Property Deed",
      category: "Property Documents",
      memberId: "mem_admin",
      memberName: "Alice Sharma",
      uploadedByUid: ADMIN_UID,
      adminUid: ADMIN_UID,
      allowedUids: [ADMIN_UID],
      fileType: "pdf",
      fileName: "deed.pdf",
      fileSizeBytes: 2048,
      isEncrypted: true,
      isBackedUp: true,
      isSample: false,
      issueDateMillis: 1700000000000,
      expiryDateMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated user cannot read or list users, families, or documents", async () => {
  await seedFamilyWithMemberAndDocs();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("families").doc("fam_1").get());
  await assertFails(unauthDb.collection("families").doc("fam_1").collection("documents").get());
  await assertFails(unauthDb.collection("family_invites").doc("MFD-1020-AB").get());
});

test("User profile is strictly isolated to owner", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = new Date();

  await assertSucceeds(adminDb.collection("users").doc(ADMIN_UID).set({
    userId: ADMIN_UID,
    displayName: "Alice",
    email: "alice@test.com",
    familyId: "",
    preferredLanguage: "en",
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(outsiderDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(outsiderDb.collection("users").doc(ADMIN_UID).set({
    userId: ADMIN_UID,
    displayName: "Eve",
    email: "eve@test.com",
    familyId: "",
    preferredLanguage: "en",
    createdAt: now,
    updatedAt: now,
  }));
});

test("Cross-family isolation blocks outsider from reading or listing another family", async () => {
  await seedFamilyWithMemberAndDocs();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertFails(outsiderDb.collection("families").doc("fam_1").get());
  const outsiderFamilies = await assertSucceeds(
    outsiderDb.collection("families").where("memberUids", "array-contains", OUTSIDER_UID).get()
  );
  if (outsiderFamilies.size !== 0) {
    throw new Error("Outsider should see 0 families");
  }
  await assertFails(outsiderDb.collection("families").where("memberUids", "array-contains", ADMIN_UID).get());
  // Outsider querying fam_1 families without filter fails
  await assertFails(outsiderDb.collection("families").get());
  // Outsider reading or listing fam_1 documents or members fails
  await assertFails(outsiderDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").get());
  await assertFails(outsiderDb.collection("families").doc("fam_1").collection("documents").where("allowedUids", "array-contains", ADMIN_UID).get());
  await assertFails(outsiderDb.collection("families").doc("fam_1").collection("members").doc("mem_admin").get());
});

test("Member can only read permitted documents and cannot access admin-only documents", async () => {
  await seedFamilyWithMemberAndDocs();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();

  await assertSucceeds(memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").get());
  await assertFails(memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").get());

  // Aligned query for member's permitted documents succeeds
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").collection("documents")
      .where("allowedUids", "array-contains", MEMBER_UID)
      .get()
  );

  // Unfiltered list query fails
  await assertFails(memberDb.collection("families").doc("fam_1").collection("documents").get());
});

test("Member cannot escalate permissions (allowedUids) or delete admin-owned document", async () => {
  await seedFamilyWithMemberAndDocs();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();

  // Member cannot alter allowedUids on a shared document
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      allowedUids: [ADMIN_UID, MEMBER_UID, OUTSIDER_UID],
      updatedAt: new Date(),
    })
  );

  // Member can rename a permitted document
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      title: "Updated Ration Card",
      updatedAt: new Date(),
    })
  );

  // Member cannot delete admin-uploaded document
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").delete()
  );
});

test("Admin can update document permissions and delete documents", async () => {
  await seedFamilyWithMemberAndDocs();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").update({
      allowedUids: [ADMIN_UID, MEMBER_UID],
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").delete()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  await seedFamilyWithMemberAndDocs();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertFails(
    adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      title: "Hacked",
      ghostField: true,
      updatedAt: new Date(),
    })
  );
});

test("Invite lookup blocks list scraping and allows exact get + self-join", async () => {
  await seedFamilyWithMemberAndDocs();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertFails(outsiderDb.collection("family_invites").get());
  await assertSucceeds(outsiderDb.collection("family_invites").doc("MFD-1020-AB").get());

  await assertSucceeds(
    outsiderDb.collection("families").doc("fam_1").update({
      memberUids: [ADMIN_UID, MEMBER_UID, OUTSIDER_UID],
      updatedAt: new Date(),
    })
  );
});

test("Server-private credential and rate-limit collections cannot be read or written by any client", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("family_credentials").doc("sharma_family").set({
      username: "sharma_family",
      passwordSalt: "salt",
      passwordHash: "hash",
    });
    await db.collection("family_auth_locks").doc("lock_1").set({
      failedAttempts: 3,
    });
    await db.collection("families").doc("fam_1").collection("device_logins").doc("dev_1").set({
      uid: MEMBER_UID,
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(adminDb.collection("family_credentials").doc("sharma_family").get());
  await assertFails(memberDb.collection("family_credentials").doc("sharma_family").get());
  await assertFails(unauthDb.collection("family_credentials").doc("sharma_family").get());
  await assertFails(adminDb.collection("family_credentials").doc("hacked").set({ username: "hacked" }));
  await assertFails(adminDb.collection("family_auth_locks").doc("lock_1").get());
  await assertFails(memberDb.collection("families").doc("fam_1").collection("device_logins").doc("dev_1").get());
});

test("Family vault encryption key secret is accessible only to authorized family members", async () => {
  await seedFamilyWithMemberAndDocs();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").set({
      familyId: "fam_1",
      adminUid: ADMIN_UID,
      keyBase64: "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      keyVersion: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Member can read vault_key but cannot overwrite or list secrets
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").get()
  );
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").update({
      keyBase64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      updatedAt: now,
    })
  );
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("secrets").get()
  );

  // Outsider cannot read vault_key
  await assertFails(
    outsiderDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").get()
  );
});

