import { LanguageCode, SecuritySettings, ThemeMode } from '../domain/models';
import { generateSalt, hashPin } from './cryptoVault';

const STORAGE_KEY = 'my_family_doc_web_prefs_v1';

const DEFAULT_SETTINGS: SecuritySettings = {
  languageCode: 'en',
  themeMode: 'light',
  pinEnabled: false,
  pinHash: '',
  pinSalt: '',
  autoBackupEnabled: true,
  backupIntervalDays: 1,
  expiryRemindersEnabled: true,
  lastLocalBackupMillis: 0,
};

export function loadSecuritySettings(): SecuritySettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<SecuritySettings>;
    return {
      languageCode: parsed.languageCode === 'kn' ? 'kn' : 'en',
      themeMode:
        parsed.themeMode === 'dark' || parsed.themeMode === 'system' ? parsed.themeMode : 'light',
      pinEnabled: Boolean(parsed.pinEnabled),
      pinHash: String(parsed.pinHash || ''),
      pinSalt: String(parsed.pinSalt || ''),
      autoBackupEnabled: parsed.autoBackupEnabled ?? true,
      backupIntervalDays: Number(parsed.backupIntervalDays || 1),
      expiryRemindersEnabled: parsed.expiryRemindersEnabled ?? true,
      lastLocalBackupMillis: Number(parsed.lastLocalBackupMillis || 0),
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSecuritySettings(next: SecuritySettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Ignore storage quota issues
  }
}

export async function createPinSettings(
  current: SecuritySettings,
  pin: string
): Promise<SecuritySettings> {
  const salt = generateSalt();
  const pinHash = await hashPin(pin, salt);
  const next: SecuritySettings = {
    ...current,
    pinEnabled: true,
    pinSalt: salt,
    pinHash,
  };
  saveSecuritySettings(next);
  return next;
}

export async function verifyStoredPin(
  current: SecuritySettings,
  candidatePin: string
): Promise<boolean> {
  if (!current.pinEnabled || !current.pinHash) return true;
  const candidateHash = await hashPin(candidatePin, current.pinSalt);
  return candidateHash === current.pinHash;
}

export function resolveEffectiveDarkTheme(themeMode: ThemeMode): boolean {
  if (themeMode === 'dark') return true;
  if (themeMode === 'light') return false;
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches;
}

export function setThemeAttribute(themeMode: ThemeMode): void {
  if (typeof document === 'undefined') return;
  const isDark = resolveEffectiveDarkTheme(themeMode);
  document.documentElement.dataset.theme = isDark ? 'dark' : 'light';
}

export function updatePreferredLanguage(
  current: SecuritySettings,
  lang: LanguageCode
): SecuritySettings {
  const next: SecuritySettings = { ...current, languageCode: lang };
  saveSecuritySettings(next);
  return next;
}
