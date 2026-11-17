export type LanguageCode = 'en' | 'kn';
export type ThemeMode = 'system' | 'light' | 'dark';
export type ExpiryStatus = 'NO_EXPIRY' | 'VALID' | 'EXPIRING_SOON' | 'EXPIRED';
export type SyncState = 'SYNCED' | 'SYNCING' | 'OFFLINE' | 'ATTENTION_NEEDED';

export interface UserProfile {
  id: string;
  userId: string;
  displayName: string;
  email: string;
  photoUrl?: string | null;
  familyId: string;
  preferredLanguage: LanguageCode;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface FamilyAccount {
  id: string;
  familyId: string;
  familyName: string;
  familyCode: string;
  adminUid: string;
  memberUids: string[];
  totalDocuments: number;
  lastBackupAtMillis: number;
  autoBackupEnabled: boolean;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface FamilyInvite {
  id: string;
  familyCode: string;
  familyId: string;
  familyName: string;
  adminUid: string;
  active: boolean;
  createdAtMillis: number;
}

export interface FamilyMember {
  id: string;
  familyId: string;
  name: string;
  relation: string;
  role: 'admin' | 'member';
  linkedUserId: string;
  email: string;
  photoBase64?: string | null;
  memberUids: string[];
  adminUid: string;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface FamilyDocument {
  id: string;
  familyId: string;
  title: string;
  category: string;
  memberId: string;
  memberName: string;
  uploadedByUid: string;
  adminUid: string;
  allowedUids: string[];
  fileType: 'pdf' | 'jpg' | 'png' | 'sample';
  fileName: string;
  fileSizeBytes: number;
  storagePath?: string | null;
  isEncrypted: boolean;
  isBackedUp: boolean;
  isSample: boolean;
  documentNumberMasked?: string | null;
  notes?: string | null;
  issueDateMillis: number;
  expiryDateMillis: number;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface BackupStatusInfo {
  syncState: SyncState;
  isOnline: boolean;
  totalDocs: number;
  backedUpDocs: number;
  localEncryptedDocs: number;
  totalStorageBytes: number;
  localVaultBytes: number;
  lastBackupAtMillis: number;
  autoBackupEnabled: boolean;
  backupIntervalDays: number;
}

export interface SecuritySettings {
  languageCode: LanguageCode;
  themeMode: ThemeMode;
  pinEnabled: boolean;
  pinHash: string;
  pinSalt: string;
  autoBackupEnabled: boolean;
  backupIntervalDays: number;
  expiryRemindersEnabled: boolean;
  lastLocalBackupMillis: number;
}

const MILLIS_PER_DAY = 86_400_000;

export function getExpiryStatus(
  expiryDateMillis: number,
  nowMillis: number = Date.now()
): ExpiryStatus {
  if (!expiryDateMillis || expiryDateMillis <= 0) return 'NO_EXPIRY';
  if (expiryDateMillis < nowMillis) return 'EXPIRED';
  const daysLeft = Math.floor((expiryDateMillis - nowMillis) / MILLIS_PER_DAY);
  if (daysLeft <= 30) return 'EXPIRING_SOON';
  return 'VALID';
}

export function getDaysUntilExpiry(
  expiryDateMillis: number,
  nowMillis: number = Date.now()
): number {
  if (!expiryDateMillis || expiryDateMillis <= 0) return Number.MAX_SAFE_INTEGER;
  return Math.floor((expiryDateMillis - nowMillis) / MILLIS_PER_DAY);
}

export function formatDateMillis(millis: number, locale = 'en-IN'): string {
  if (!millis || millis <= 0) return '—';
  try {
    return new Intl.DateTimeFormat(locale, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(new Date(millis));
  } catch {
    return new Date(millis).toISOString().slice(0, 10);
  }
}

export function formatFileSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
