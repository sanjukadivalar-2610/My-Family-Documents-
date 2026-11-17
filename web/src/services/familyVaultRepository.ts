import {
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  Firestore,
  getDoc,
  increment,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  Unsubscribe,
  updateDoc,
  where,
} from 'firebase/firestore';
import { Auth } from 'firebase/auth';
import { deleteObject, FirebaseStorage, getBytes, ref, uploadBytes } from 'firebase/storage';
import {
  FamilyAccount,
  FamilyDocument,
  FamilyInvite,
  FamilyMember,
  LanguageCode,
  UserProfile,
} from '../domain/models';

function tsToMillis(val: unknown): number {
  if (val instanceof Timestamp) return val.toMillis();
  if (val && typeof (val as { toMillis?: () => number }).toMillis === 'function') {
    return (val as { toMillis: () => number }).toMillis();
  }
  return 0;
}

function generateId(prefix: string, len = 12): string {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  const hex = Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, len);
  return `${prefix}_${hex}`;
}

function generateFamilyCode(): string {
  const digits = Math.floor(1000 + Math.random() * 9000);
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const a = chars[Math.floor(Math.random() * chars.length)];
  const b = chars[Math.floor(Math.random() * chars.length)];
  return `MFD-${digits}-${a}${b}`;
}

export class FamilyVaultRepository {
  constructor(
    private readonly db: Firestore,
    private readonly auth: Auth,
    private readonly storage: FirebaseStorage | null
  ) {}

  private requireUser() {
    const user = this.auth.currentUser;
    if (!user) throw new Error('User must be authenticated before accessing the Family Vault.');
    return user;
  }

  async ensureUserProfile(preferredLanguage: LanguageCode = 'en'): Promise<UserProfile> {
    const user = this.requireUser();
    const userRef = doc(this.db, 'users', user.uid);
    const snap = await getDoc(userRef);
    if (snap.exists()) {
      const d = snap.data();
      return {
        id: snap.id,
        userId: String(d.userId || user.uid),
        displayName: String(d.displayName || user.displayName || 'Family Member'),
        email: String(d.email || user.email || ''),
        photoUrl: d.photoUrl ? String(d.photoUrl) : null,
        familyId: String(d.familyId || ''),
        preferredLanguage: d.preferredLanguage === 'kn' ? 'kn' : 'en',
        createdAtMillis: tsToMillis(d.createdAt),
        updatedAtMillis: tsToMillis(d.updatedAt),
      };
    }

    const displayName = (user.displayName?.trim() || user.email?.split('@')[0] || 'Family Member').slice(0, 100);
    const payload: Record<string, unknown> = {
      userId: user.uid,
      displayName,
      email: (user.email || '').slice(0, 160),
      familyId: '',
      preferredLanguage: preferredLanguage === 'kn' ? 'kn' : 'en',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (user.photoURL && user.photoURL.length <= 500) {
      payload.photoUrl = user.photoURL;
    }
    await setDoc(userRef, payload);

    return {
      id: user.uid,
      userId: user.uid,
      displayName,
      email: String(payload.email),
      photoUrl: (payload.photoUrl as string) || null,
      familyId: '',
      preferredLanguage,
      createdAtMillis: Date.now(),
      updatedAtMillis: Date.now(),
    };
  }

  async updateUserLanguage(languageCode: LanguageCode): Promise<void> {
    const user = this.requireUser();
    await updateDoc(doc(this.db, 'users', user.uid), {
      preferredLanguage: languageCode === 'kn' ? 'kn' : 'en',
      updatedAt: serverTimestamp(),
    });
  }

  observeUserProfile(
    uid: string,
    onData: (profile: UserProfile | null) => void,
    onError: (err: Error) => void
  ): Unsubscribe {
    return onSnapshot(
      doc(this.db, 'users', uid),
      (snap) => {
        if (!snap.exists()) {
          onData(null);
          return;
        }
        const d = snap.data();
        onData({
          id: snap.id,
          userId: String(d.userId || uid),
          displayName: String(d.displayName || ''),
          email: String(d.email || ''),
          photoUrl: d.photoUrl ? String(d.photoUrl) : null,
          familyId: String(d.familyId || ''),
          preferredLanguage: d.preferredLanguage === 'kn' ? 'kn' : 'en',
          createdAtMillis: tsToMillis(d.createdAt),
          updatedAtMillis: tsToMillis(d.updatedAt),
        });
      },
      (err) => onError(err)
    );
  }

  observeFamilyAccount(
    familyId: string,
    onData: (family: FamilyAccount | null) => void,
    onError: (err: Error) => void
  ): Unsubscribe {
    return onSnapshot(
      doc(this.db, 'families', familyId),
      (snap) => {
        if (!snap.exists()) {
          onData(null);
          return;
        }
        const d = snap.data();
        onData({
          id: snap.id,
          familyId: String(d.familyId || snap.id),
          familyName: String(d.familyName || ''),
          familyCode: String(d.familyCode || ''),
          adminUid: String(d.adminUid || ''),
          memberUids: Array.isArray(d.memberUids) ? d.memberUids.map(String) : [],
          totalDocuments: Number(d.totalDocuments || 0),
          lastBackupAtMillis: tsToMillis(d.lastBackupAt),
          autoBackupEnabled: Boolean(d.autoBackupEnabled ?? true),
          createdAtMillis: tsToMillis(d.createdAt),
          updatedAtMillis: tsToMillis(d.updatedAt),
        });
      },
      (err) => onError(err)
    );
  }

  observeFamilyMembers(
    familyId: string,
    uid: string,
    onData: (members: FamilyMember[]) => void,
    onError: (err: Error) => void
  ): Unsubscribe {
    const q = query(
      collection(this.db, 'families', familyId, 'members'),
      where('memberUids', 'array-contains', uid)
    );
    return onSnapshot(
      q,
      (snap) => {
        const list: FamilyMember[] = snap.docs.map((s) => {
          const d = s.data();
          return {
            id: s.id,
            familyId: String(d.familyId || familyId),
            name: String(d.name || ''),
            relation: String(d.relation || 'Member'),
            role: d.role === 'admin' ? 'admin' : 'member',
            linkedUserId: String(d.linkedUserId || ''),
            email: String(d.email || ''),
            photoBase64: d.photoBase64 ? String(d.photoBase64) : null,
            memberUids: Array.isArray(d.memberUids) ? d.memberUids.map(String) : [],
            adminUid: String(d.adminUid || ''),
            createdAtMillis: tsToMillis(d.createdAt),
            updatedAtMillis: tsToMillis(d.updatedAt),
          };
        });
        list.sort((a, b) => a.createdAtMillis - b.createdAtMillis);
        onData(list);
      },
      (err) => onError(err)
    );
  }

  observePermittedDocuments(
    familyId: string,
    uid: string,
    onData: (docs: FamilyDocument[]) => void,
    onError: (err: Error) => void
  ): Unsubscribe {
    const q = query(
      collection(this.db, 'families', familyId, 'documents'),
      where('allowedUids', 'array-contains', uid)
    );
    return onSnapshot(
      q,
      (snap) => {
        const list: FamilyDocument[] = snap.docs.map((s) => {
          const d = s.data();
          const ft = String(d.fileType || 'pdf');
          const fileType: FamilyDocument['fileType'] =
            ft === 'jpg' || ft === 'png' || ft === 'sample' ? ft : 'pdf';
          return {
            id: s.id,
            familyId: String(d.familyId || familyId),
            title: String(d.title || ''),
            category: String(d.category || 'Other Documents'),
            memberId: String(d.memberId || ''),
            memberName: String(d.memberName || ''),
            uploadedByUid: String(d.uploadedByUid || ''),
            adminUid: String(d.adminUid || ''),
            allowedUids: Array.isArray(d.allowedUids) ? d.allowedUids.map(String) : [],
            fileType,
            fileName: String(d.fileName || ''),
            fileSizeBytes: Number(d.fileSizeBytes || 0),
            storagePath: d.storagePath ? String(d.storagePath) : null,
            isEncrypted: Boolean(d.isEncrypted ?? true),
            isBackedUp: Boolean(d.isBackedUp ?? false),
            isSample: Boolean(d.isSample ?? false),
            documentNumberMasked: d.documentNumberMasked ? String(d.documentNumberMasked) : null,
            notes: d.notes ? String(d.notes) : null,
            issueDateMillis: Number(d.issueDateMillis || 0),
            expiryDateMillis: Number(d.expiryDateMillis || 0),
            createdAtMillis: tsToMillis(d.createdAt),
            updatedAtMillis: tsToMillis(d.updatedAt),
          };
        });
        list.sort((a, b) => (b.updatedAtMillis || b.createdAtMillis) - (a.updatedAtMillis || a.createdAtMillis));
        onData(list);
      },
      (err) => onError(err)
    );
  }

  async createFamily(
    familyName: string,
    creatorName: string,
    preferredLanguage: LanguageCode = 'en'
  ): Promise<string> {
    const user = this.requireUser();
    const cleanFamilyName = familyName.trim().slice(0, 80);
    if (cleanFamilyName.length < 2) {
      throw new Error('Family name must be at least 2 characters.');
    }
    const cleanCreatorName = (creatorName.trim() || user.displayName || 'Admin').slice(0, 80);

    await this.ensureUserProfile(preferredLanguage);

    const familyId = generateId('fam', 12);
    const familyCode = generateFamilyCode();
    const familyRef = doc(this.db, 'families', familyId);
    const inviteRef = doc(this.db, 'family_invites', familyCode);
    const memberRef = doc(this.db, 'families', familyId, 'members', user.uid);
    const userRef = doc(this.db, 'users', user.uid);

    await setDoc(familyRef, {
      familyId,
      familyName: cleanFamilyName,
      familyCode,
      adminUid: user.uid,
      memberUids: [user.uid],
      totalDocuments: 0,
      autoBackupEnabled: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });

    await setDoc(inviteRef, {
      familyCode,
      familyId,
      familyName: cleanFamilyName,
      adminUid: user.uid,
      active: true,
      createdAt: serverTimestamp(),
    });

    await setDoc(memberRef, {
      familyId,
      name: cleanCreatorName,
      relation: 'Self',
      role: 'admin',
      linkedUserId: user.uid,
      email: (user.email || '').slice(0, 160),
      memberUids: [user.uid],
      adminUid: user.uid,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });

    await updateDoc(userRef, {
      familyId,
      displayName: cleanCreatorName.slice(0, 100),
      updatedAt: serverTimestamp(),
    });

    return familyId;
  }

  async joinFamilyByCode(
    rawCode: string,
    memberName: string,
    preferredLanguage: LanguageCode = 'en'
  ): Promise<string> {
    const user = this.requireUser();
    const code = rawCode.trim().toUpperCase();
    if (!/^[A-Za-z0-9_-]{6,24}$/.test(code)) {
      throw new Error('Invalid Family ID format.');
    }

    await this.ensureUserProfile(preferredLanguage);
    const inviteSnap = await getDoc(doc(this.db, 'family_invites', code));
    if (!inviteSnap.exists()) {
      throw new Error('Family ID not found.');
    }
    const invite = inviteSnap.data() as FamilyInvite;
    if (!invite.active) {
      throw new Error('This Family ID is no longer active.');
    }

    const familyRef = doc(this.db, 'families', invite.familyId);
    await updateDoc(familyRef, {
      memberUids: arrayUnion(user.uid),
      updatedAt: serverTimestamp(),
    });

    const familySnap = await getDoc(familyRef);
    const familyData = familySnap.data();
    if (!familyData) {
      throw new Error('Unable to load family after joining.');
    }

    const cleanName = (memberName.trim() || user.displayName || 'Family Member').slice(0, 80);
    const memberRef = doc(this.db, 'families', invite.familyId, 'members', user.uid);
    const existingMember = await getDoc(memberRef);
    if (!existingMember.exists()) {
      const memberUids = Array.isArray(familyData.memberUids)
        ? familyData.memberUids.map(String)
        : Array.from(new Set([String(familyData.adminUid), user.uid]));
      await setDoc(memberRef, {
        familyId: invite.familyId,
        name: cleanName,
        relation: 'Member',
        role: 'member',
        linkedUserId: user.uid,
        email: (user.email || '').slice(0, 160),
        memberUids,
        adminUid: String(familyData.adminUid),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    }

    await updateDoc(doc(this.db, 'users', user.uid), {
      familyId: invite.familyId,
      displayName: cleanName.slice(0, 100),
      updatedAt: serverTimestamp(),
    });

    return invite.familyId;
  }

  async syncMemberUidsIfAdmin(family: FamilyAccount, members: FamilyMember[]): Promise<void> {
    const user = this.auth.currentUser;
    if (!user || family.adminUid !== user.uid) return;
    const expectedKey = [...family.memberUids].sort().join(',');
    for (const member of members) {
      if (!member.id) continue;
      const actualKey = [...member.memberUids].sort().join(',');
      if (actualKey !== expectedKey) {
        try {
          await updateDoc(doc(this.db, 'families', family.familyId, 'members', member.id), {
            memberUids: family.memberUids,
            updatedAt: serverTimestamp(),
          });
        } catch {
          // Ignore non-fatal sync contention
        }
      }
    }
  }

  async addOrUpdateMember(params: {
    family: FamilyAccount;
    existingMember?: FamilyMember | null;
    name: string;
    relation: string;
    role: 'admin' | 'member';
    email: string;
    photoBase64?: string | null;
  }): Promise<string> {
    const user = this.requireUser();
    const { family, existingMember, name, relation, role, email, photoBase64 } = params;
    const cleanName = name.trim().slice(0, 80);
    if (!cleanName) throw new Error('Member name is required.');
    const cleanRelation = (relation.trim() || 'Member').slice(0, 40);
    const cleanRole = role === 'admin' ? 'admin' : 'member';
    const cleanEmail = email.trim().slice(0, 160);
    const memberId = existingMember?.id || generateId('mem', 12);
    const memberRef = doc(this.db, 'families', family.familyId, 'members', memberId);

    if (!existingMember) {
      const payload: Record<string, unknown> = {
        familyId: family.familyId,
        name: cleanName,
        relation: cleanRelation,
        role: cleanRole,
        linkedUserId: '',
        email: cleanEmail,
        memberUids: family.memberUids,
        adminUid: family.adminUid,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      };
      if (photoBase64 && photoBase64.length <= 120_000) {
        payload.photoBase64 = photoBase64;
      }
      await setDoc(memberRef, payload);
    } else {
      const updates: Record<string, unknown> = {
        name: cleanName,
        relation: cleanRelation,
        updatedAt: serverTimestamp(),
      };
      if (family.adminUid === user.uid) {
        updates.role = existingMember.linkedUserId === family.adminUid ? 'admin' : cleanRole;
        updates.email = cleanEmail;
        updates.memberUids = family.memberUids;
      }
      if (photoBase64 && photoBase64.length <= 120_000) {
        updates.photoBase64 = photoBase64;
      }
      await updateDoc(memberRef, updates);
    }
    return memberId;
  }

  async deleteMember(familyId: string, memberId: string): Promise<void> {
    this.requireUser();
    await deleteDoc(doc(this.db, 'families', familyId, 'members', memberId));
  }

  async saveNewDocumentMetadata(params: {
    family: FamilyAccount;
    docId: string;
    title: string;
    category: string;
    memberId: string;
    memberName: string;
    allowedUids: string[];
    fileType: 'pdf' | 'jpg' | 'png' | 'sample';
    fileName: string;
    fileSizeBytes: number;
    isEncrypted: boolean;
    isBackedUp: boolean;
    isSample: boolean;
    documentNumberMasked?: string | null;
    notes?: string | null;
    issueDateMillis: number;
    expiryDateMillis: number;
  }): Promise<void> {
    const user = this.requireUser();
    const finalAllowed = Array.from(
      new Set([...params.allowedUids, params.family.adminUid, user.uid])
    ).slice(0, 25);

    const payload: Record<string, unknown> = {
      familyId: params.family.familyId,
      title: (params.title.trim() || 'Untitled Document').slice(0, 120),
      category: params.category.slice(0, 60),
      memberId: params.memberId,
      memberName: (params.memberName.trim() || 'Family').slice(0, 80),
      uploadedByUid: user.uid,
      adminUid: params.family.adminUid,
      allowedUids: finalAllowed,
      fileType: params.fileType,
      fileName: params.fileName.slice(0, 160),
      fileSizeBytes: Math.max(0, Math.round(params.fileSizeBytes)),
      isEncrypted: params.isEncrypted,
      isBackedUp: params.isBackedUp,
      isSample: params.isSample,
      issueDateMillis: Math.max(0, Math.round(params.issueDateMillis)),
      expiryDateMillis: Math.max(0, Math.round(params.expiryDateMillis)),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };

    if (params.documentNumberMasked?.trim()) {
      payload.documentNumberMasked = params.documentNumberMasked.trim().slice(0, 40);
    }
    if (params.notes?.trim()) {
      payload.notes = params.notes.trim().slice(0, 500);
    }

    await setDoc(doc(this.db, 'families', params.family.familyId, 'documents', params.docId), payload);

    try {
      await updateDoc(doc(this.db, 'families', params.family.familyId), {
        totalDocuments: increment(1),
        updatedAt: serverTimestamp(),
      });
    } catch {
      // Counter increment is best-effort
    }
  }

  async renameOrUpdateDates(params: {
    familyId: string;
    docId: string;
    newTitle: string;
    category: string;
    issueDateMillis: number;
    expiryDateMillis: number;
    notes?: string | null;
    documentNumberMasked?: string | null;
  }): Promise<void> {
    this.requireUser();
    const updates: Record<string, unknown> = {
      title: (params.newTitle.trim() || 'Untitled Document').slice(0, 120),
      category: params.category.slice(0, 60),
      issueDateMillis: Math.max(0, Math.round(params.issueDateMillis)),
      expiryDateMillis: Math.max(0, Math.round(params.expiryDateMillis)),
      updatedAt: serverTimestamp(),
    };
    if (params.notes !== undefined && params.notes !== null) {
      updates.notes = params.notes.slice(0, 500);
    }
    if (params.documentNumberMasked !== undefined && params.documentNumberMasked !== null) {
      updates.documentNumberMasked = params.documentNumberMasked.slice(0, 40);
    }
    await updateDoc(doc(this.db, 'families', params.familyId, 'documents', params.docId), updates);
  }

  async updateDocumentPermissions(
    family: FamilyAccount,
    document: FamilyDocument,
    allowedMemberUids: string[]
  ): Promise<void> {
    const user = this.requireUser();
    if (family.adminUid !== user.uid) {
      throw new Error('Only the Family Admin can change permissions.');
    }
    const merged = Array.from(
      new Set([...allowedMemberUids, family.adminUid, document.uploadedByUid])
    ).slice(0, 25);

    await updateDoc(doc(this.db, 'families', family.familyId, 'documents', document.id), {
      allowedUids: merged,
      updatedAt: serverTimestamp(),
    });
  }

  async deleteDocument(familyId: string, docId: string, storagePath?: string | null): Promise<void> {
    this.requireUser();
    await deleteDoc(doc(this.db, 'families', familyId, 'documents', docId));
    if (storagePath && this.storage) {
      try {
        await deleteObject(ref(this.storage, storagePath));
      } catch {
        // Ignore missing cloud blob
      }
    }
    try {
      const famRef = doc(this.db, 'families', familyId);
      const snap = await getDoc(famRef);
      const total = Number(snap.data()?.totalDocuments || 0);
      if (total > 0) {
        await updateDoc(famRef, {
          totalDocuments: total - 1,
          updatedAt: serverTimestamp(),
        });
      }
    } catch {
      // Non-fatal counter update
    }
  }

  async renameFamily(family: FamilyAccount, newName: string): Promise<void> {
    this.requireUser();
    const clean = newName.trim().slice(0, 80);
    if (clean.length < 2) throw new Error('Family name too short.');
    await updateDoc(doc(this.db, 'families', family.familyId), {
      familyName: clean,
      updatedAt: serverTimestamp(),
    });
    try {
      await updateDoc(doc(this.db, 'family_invites', family.familyCode), {
        familyName: clean,
      });
    } catch {
      // Optional invite name sync
    }
  }

  async updateFamilyAutoBackup(familyId: string, enabled: boolean): Promise<void> {
    this.requireUser();
    await updateDoc(doc(this.db, 'families', familyId), {
      autoBackupEnabled: enabled,
      updatedAt: serverTimestamp(),
    });
  }

  async uploadEncryptedPayloadToStorage(
    familyId: string,
    docId: string,
    encryptedBytes: Uint8Array
  ): Promise<string> {
    if (!this.storage) {
      throw new Error('Firebase Storage is not initialized.');
    }
    const path = `families/${familyId}/documents/${docId}.enc`;
    const storageRef = ref(this.storage, path);
    await uploadBytes(storageRef, encryptedBytes, {
      contentType: 'application/octet-stream',
    });
    await updateDoc(doc(this.db, 'families', familyId, 'documents', docId), {
      storagePath: path,
      isBackedUp: true,
      updatedAt: serverTimestamp(),
    });
    return path;
  }

  async downloadEncryptedPayloadFromStorage(storagePath: string): Promise<Uint8Array> {
    if (!this.storage) {
      throw new Error('Firebase Storage is not initialized.');
    }
    const buf = await getBytes(ref(this.storage, storagePath), 25 * 1024 * 1024);
    return new Uint8Array(buf);
  }

  async markBackupTimestamp(familyId: string): Promise<void> {
    this.requireUser();
    await updateDoc(doc(this.db, 'families', familyId), {
      lastBackupAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  }
}
