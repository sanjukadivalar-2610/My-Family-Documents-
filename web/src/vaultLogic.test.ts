import { describe, expect, it } from 'vitest';
import { DOCUMENT_CATEGORIES, getCategoryByKey, getLocalizedCategoryLabel } from './domain/documentCategory';
import {
  formatFileSize,
  getDaysUntilExpiry,
  getExpiryStatus,
} from './domain/models';
import { EnglishStrings, KannadaStrings, stringsForLanguage } from './localization/appStrings';
import {
  decryptBytesWithKey,
  encryptBytesWithKey,
  generateSalt,
  hashPin,
} from './services/cryptoVault';
import { buildSamplePdfBytes, getSampleTemplates } from './services/sampleDocumentFactory';

describe('My Family Doc Domain & Vault Logic', () => {
  it('defines all 12 document categories with English and Kannada labels', () => {
    expect(DOCUMENT_CATEGORIES).toHaveLength(12);
    const keys = DOCUMENT_CATEGORIES.map((c) => c.storageKey);
    expect(keys).toEqual([
      'Aadhaar Card',
      'PAN Card',
      'Voter ID',
      'Ration Card',
      'Driving Licence',
      'Passport',
      'Education Documents',
      'Bank Documents',
      'Property Documents',
      'Medical Documents',
      'Certificates',
      'Other Documents',
    ]);
    for (const cat of DOCUMENT_CATEGORIES) {
      expect(getLocalizedCategoryLabel(cat, 'en').length).toBeGreaterThan(0);
      expect(getLocalizedCategoryLabel(cat, 'kn').length).toBeGreaterThan(0);
    }
    expect(getCategoryByKey('NonExistent').storageKey).toBe('Other Documents');
  });

  it('provides full bilingual localization for English and Kannada', () => {
    const en = stringsForLanguage('en');
    const kn = stringsForLanguage('kn');
    expect(en).toBe(EnglishStrings);
    expect(kn).toBe(KannadaStrings);
    expect(en.appName).toBe('My Family Doc');
    expect(kn.appName).toBe('ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್');
    for (const key of Object.keys(EnglishStrings) as Array<keyof typeof EnglishStrings>) {
      expect(String(kn[key]).trim().length).toBeGreaterThan(0);
    }
  });

  it('computes expiry status accurately for valid, expiring soon, expired, and none', () => {
    const now = 1_750_000_000_000;
    const day = 86_400_000;
    expect(getExpiryStatus(0, now)).toBe('NO_EXPIRY');
    expect(getExpiryStatus(now - day, now)).toBe('EXPIRED');
    expect(getExpiryStatus(now + 10 * day, now)).toBe('EXPIRING_SOON');
    expect(getExpiryStatus(now + 90 * day, now)).toBe('VALID');
    expect(getDaysUntilExpiry(now + 12 * day, now)).toBe(12);
  });

  it('formats byte sizes cleanly', () => {
    expect(formatFileSize(0)).toBe('0 B');
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(2048)).toBe('2 KB');
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB');
  });

  it('generates valid synthetic PDF sample bytes with zero external dependencies', () => {
    const tpl = getSampleTemplates('Demo User').find((t) => t.fileType === 'pdf')!;
    const bytes = buildSamplePdfBytes(tpl, 'Demo User');
    const text = new TextDecoder().decode(bytes);
    expect(text.startsWith('%PDF-1.4')).toBe(true);
    expect(text.endsWith('%%EOF')).toBe(true);
  });

  it('encrypts and decrypts payloads using AES-256-GCM framing [1B ivLen][IV][cipher]', async () => {
    const key = await crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
    const plain = new TextEncoder().encode('Confidential Family Record 1234');
    const encrypted = await encryptBytesWithKey(plain, key);
    expect(encrypted[0]).toBe(12);
    expect(encrypted.byteLength).toBeGreaterThan(plain.byteLength + 13);

    const decrypted = await decryptBytesWithKey(encrypted, key);
    expect(new TextDecoder().decode(decrypted)).toBe('Confidential Family Record 1234');
  });

  it('hashes PINs deterministically with unique salts', async () => {
    const salt1 = generateSalt();
    const salt2 = generateSalt();
    expect(salt1).not.toBe(salt2);
    const h1 = await hashPin('1234', salt1);
    const h2 = await hashPin('1234', salt1);
    const h3 = await hashPin('4321', salt1);
    expect(h1).toBe(h2);
    expect(h1).not.toBe(h3);
    expect(h1.length).toBe(64);
  });
});
