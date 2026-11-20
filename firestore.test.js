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

test("Normal member can remove only self and own uploads, never admin or other members", async () => {
  await seedFamilyWithMemberAndDocs();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date();
    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Alice",
      email: "alice@test.com",
      familyId: "fam_1",
      preferredLanguage: "en",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(MEMBER_UID).set({
      userId: MEMBER_UID,
      displayName: "Bob",
      email: "bob@test.com",
      familyId: "fam_1",
      preferredLanguage: "en",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_1").collection("members").doc("mem_bob").set({
      familyId: "fam_1",
      name: "Bob Sharma",
      relation: "Spouse",
      role: "member",
      linkedUserId: MEMBER_UID,
      email: "bob@test.com",
      memberUids: [ADMIN_UID, MEMBER_UID],
      adminUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_1").collection("documents").doc("doc_bob_own").set({
      familyId: "fam_1",
      title: "Bob Passport",
      category: "Passport",
      memberId: "mem_bob",
      memberName: "Bob Sharma",
      uploadedByUid: MEMBER_UID,
      adminUid: ADMIN_UID,
      allowedUids: [ADMIN_UID, MEMBER_UID],
      fileType: "pdf",
      fileName: "bob_passport.pdf",
      fileSizeBytes: 2048,
      isEncrypted: true,
      isBackedUp: false,
      isSample: false,
      issueDateMillis: 1700000000000,
      expiryDateMillis: 1900000000000,
      createdAt: now,
      updatedAt: now,
    });
  });

  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = new Date();

  // Member CANNOT delete another member's profile, family doc, invite, or admin's member record
  await assertFails(memberDb.collection("users").doc(ADMIN_UID).delete());
  await assertFails(memberDb.collection("families").doc("fam_1").delete());
  await assertFails(memberDb.collection("family_invites").doc("MFD-1020-AB").delete());
  await assertFails(memberDb.collection("families").doc("fam_1").collection("members").doc("mem_admin").delete());
  // Member CANNOT remove Admin from allowedUids
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      allowedUids: [MEMBER_UID],
      updatedAt: now,
    })
  );

  // Member CAN delete own uploaded doc, remove self from shared doc allowedUids, delete own member doc, remove self from memberUids, delete own user profile
  await assertSucceeds(memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_bob_own").delete());
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      allowedUids: [ADMIN_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(memberDb.collection("families").doc("fam_1").collection("members").doc("mem_bob").delete());
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").update({
      memberUids: [ADMIN_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(memberDb.collection("users").doc(MEMBER_UID).delete());
});

test("Admin can delete vault_key secret and entire family, without touching other members user profiles", async () => {
  await seedFamilyWithMemberAndDocs();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
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

  // Member cannot delete vault_key
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  await assertFails(memberDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").delete());

  // Admin deletes child docs -> vault_key -> invite -> family
  await assertSucceeds(adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").delete());
  await assertSucceeds(adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").delete());
  await assertSucceeds(adminDb.collection("families").doc("fam_1").collection("members").doc("mem_admin").delete());
  await assertSucceeds(adminDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").delete());
  await assertSucceeds(adminDb.collection("family_invites").doc("MFD-1020-AB").delete());
  await assertSucceeds(adminDb.collection("families").doc("fam_1").delete());
});

test("Admin can safely transfer ownership to an existing family member, and cannot transfer to an outsider", async () => {
  await seedFamilyWithMemberAndDocs();
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("families").doc("fam_1").collection("secrets").doc("vault_key").set({
      familyId: "fam_1",
      adminUid: ADMIN_UID,
      keyBase64: "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      keyVersion: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_1").collection("members").doc("mem_bob").set({
      familyId: "fam_1",
      name: "Bob Sharma",
      relation: "Spouse",
      role: "member",
      linkedUserId: MEMBER_UID,
      email: "bob@test.com",
      memberUids: [ADMIN_UID, MEMBER_UID],
      adminUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const bobDb = testEnv.authenticatedContext(MEMBER_UID).firestore();

  // Admin cannot transfer to outsider
  await assertFails(
    adminDb.collection("families").doc("fam_1").update({
      adminUid: OUTSIDER_UID,
      updatedAt: now,
    })
  );

  // Transfer child docs + secret + invite BEFORE updating families/fam_1.adminUid
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("secrets").doc("vault_key").update({
      adminUid: MEMBER_UID,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("family_invites").doc("MFD-1020-AB").update({
      adminUid: MEMBER_UID,
    })
  );
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("members").doc("mem_bob").update({
      adminUid: MEMBER_UID,
      role: "admin",
      memberUids: [MEMBER_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("members").doc("mem_admin").delete()
  );
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_shared").update({
      adminUid: MEMBER_UID,
      allowedUids: [MEMBER_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").update({
      adminUid: MEMBER_UID,
      allowedUids: [MEMBER_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").update({
      adminUid: MEMBER_UID,
      updatedAt: now,
    })
  );
  // Outgoing admin now leaves memberUids cleanly as non-admin
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").update({
      memberUids: [MEMBER_UID],
      updatedAt: now,
    })
  );

  // Bob is now the Admin and can read/manage documents
  await assertSucceeds(
    bobDb.collection("families").doc("fam_1").collection("documents").doc("doc_admin_only").get()
  );
  await assertSucceeds(
    bobDb.collection("families").doc("fam_1").update({
      familyName: "Bob Family Vault",
      updatedAt: now,
    })
  );
});

test("Global /app_config/live allows authenticated get and blocks unauthenticated reads and client writes", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("app_config").doc("live").set({
      configVersion: 2,
      noticeActive: true,
      noticeTitleEn: "Holiday Hours",
      noticeTitleKn: "ರಜಾ ಸೂಚನೆ",
      noticeBodyEn: "Sync service is operating normally.",
      noticeBodyKn: "ಸಿಂಕ್ ಸೇವೆ ಸಾಮಾನ್ಯವಾಗಿ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತಿದೆ.",
      noticeLevel: "info",
      homeBannerLabelEn: "Verified Family Docs",
      homeBannerLabelKn: "ದೃಢೀಕೃತ ಕುಟುಂಬ ದಾಖಲೆಗಳು",
      extraCategories: [{ key: "Insurance Policy", labelEn: "Insurance Policy", labelKn: "ವಿಮಾ ಪಾಲಿಸಿ" }],
      expiryWarningDays: 45,
      showSampleDocs: true,
      minRecommendedVersionCode: 1,
    });
  });

  const anonDb = testEnv.unauthenticatedContext().firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();

  await assertFails(anonDb.collection("app_config").doc("live").get());
  await assertSucceeds(memberDb.collection("app_config").doc("live").get());
  await assertFails(memberDb.collection("app_config").get());
  await assertFails(
    memberDb.collection("app_config").doc("live").set({
      noticeActive: true,
      noticeTitleEn: "Spoofed",
    })
  );
});

test("Family /families/{familyId}/config/live allows Admin write and member read, rejects member/outsider writes", async () => {
  await seedFamilyWithMemberAndDocs();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const validConfigPayload = {
    familyId: "fam_1",
    adminUid: ADMIN_UID,
    noticeActive: true,
    noticeTitleEn: "Passport Renewal Reminder",
    noticeTitleKn: "ಪಾಸ್‌ಪೋರ್ಟ್ ನವೀಕರಣ",
    noticeBodyEn: "Please renew before June.",
    noticeBodyKn: "ಜೂನ್ ಮೊದಲು ನವೀಕರಿಸಿ.",
    noticeLevel: "warning",
    homeBannerLabelEn: "Sharma Family Vault",
    homeBannerLabelKn: "ಶರ್ಮಾ ಕುಟುಂಬ ವಾಲ್ಟ್",
    extraCategories: [{ key: "Insurance Policy", labelEn: "Insurance Policy", labelKn: "ವಿಮಾ ಪಾಲಿಸಿ" }],
    expiryWarningDays: 45,
    showSampleDocs: false,
    createdAt: now,
    updatedAt: now,
  };

  // Non-admin member cannot create or write family config
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("config").doc("live").set(validConfigPayload)
  );
  // Outsider cannot read or write family config
  await assertFails(
    outsiderDb.collection("families").doc("fam_1").collection("config").doc("live").get()
  );

  // Admin can create and update family cloud config
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("config").doc("live").set(validConfigPayload)
  );
  // Family member can read synced family config
  await assertSucceeds(
    memberDb.collection("families").doc("fam_1").collection("config").doc("live").get()
  );

  // Admin can update categories/notices/settings cleanly without changing createdAt
  await assertSucceeds(
    adminDb.collection("families").doc("fam_1").collection("config").doc("live").update({
      expiryWarningDays: 60,
      noticeBodyEn: "Updated without APK rebuild!",
      updatedAt: now,
    })
  );

  // Non-admin member still cannot overwrite synced config
  await assertFails(
    memberDb.collection("families").doc("fam_1").collection("config").doc("live").update({
      expiryWarningDays: 10,
      updatedAt: now,
    })
  );
});



