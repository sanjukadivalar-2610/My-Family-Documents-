/**
 * Web Crypto AES-256-GCM + IndexedDB encrypted vault.
 * Framing matches the Android app's DocumentCryptoManager:
 * [1 byte ivLength][iv bytes][AES-GCM ciphertext+tag]
 */

const DB_NAME = 'my_family_doc_vault_v1';
const DB_VERSION = 1;
const STORE_KEYS = 'vault_keys';
const STORE_DOCS = 'encrypted_docs';
const MASTER_KEY_ID = 'master_aes_256_gcm';

function openVaultDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is unavailable in this environment.'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_KEYS)) {
        db.createObjectStore(STORE_KEYS);
      }
      if (!db.objectStoreNames.contains(STORE_DOCS)) {
        db.createObjectStore(STORE_DOCS, { keyPath: 'docId' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('Failed to open encrypted vault DB'));
  });
}

async function getOrCreateAesKey(): Promise<CryptoKey> {
  const db = await openVaultDb();
  const existing = await new Promise<CryptoKey | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_KEYS, 'readonly');
    const req = tx.objectStore(STORE_KEYS).get(MASTER_KEY_ID);
    req.onsuccess = () => resolve(req.result as CryptoKey | undefined);
    req.onerror = () => reject(req.error);
  });
  if (existing) return existing;

  const key = await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    false, // non-extractable key inside IndexedDB
    ['encrypt', 'decrypt']
  );

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_KEYS, 'readwrite');
    tx.objectStore(STORE_KEYS).put(key, MASTER_KEY_ID);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });

  return key;
}

export async function encryptBytesWithKey(
  plainBytes: Uint8Array,
  key: CryptoKey
): Promise<Uint8Array> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encryptedBuffer = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv as BufferSource, tagLength: 128 },
    key,
    plainBytes as BufferSource
  );
  const cipherText = new Uint8Array(encryptedBuffer);
  const payload = new Uint8Array(1 + iv.length + cipherText.length);
  payload[0] = iv.length;
  payload.set(iv, 1);
  payload.set(cipherText, 1 + iv.length);
  return payload;
}

export async function decryptBytesWithKey(
  encryptedPayload: Uint8Array,
  key: CryptoKey
): Promise<Uint8Array> {
  if (encryptedPayload.byteLength <= 14) {
    throw new Error('Invalid encrypted payload');
  }
  const ivLength = encryptedPayload[0] & 0xff;
  const iv = encryptedPayload.slice(1, 1 + ivLength);
  const cipherText = encryptedPayload.slice(1 + ivLength);
  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as BufferSource, tagLength: 128 },
    key,
    cipherText as BufferSource
  );
  return new Uint8Array(decrypted);
}

export interface LocalEncryptedStats {
  count: number;
  totalBytes: number;
}

interface StoredEncryptedRecord {
  docId: string;
  payload: ArrayBuffer;
  updatedAt: number;
}

export async function saveEncryptedDocument(docId: string, plainBytes: Uint8Array): Promise<void> {
  const key = await getOrCreateAesKey();
  const encrypted = await encryptBytesWithKey(plainBytes, key);
  await writeRawEncryptedPayload(docId, encrypted);
}

export async function writeRawEncryptedPayload(
  docId: string,
  encryptedBytes: Uint8Array
): Promise<void> {
  const db = await openVaultDb();
  const record: StoredEncryptedRecord = {
    docId,
    payload: encryptedBytes.slice().buffer,
    updatedAt: Date.now(),
  };
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_DOCS, 'readwrite');
    tx.objectStore(STORE_DOCS).put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function readEncryptedPayload(docId: string): Promise<Uint8Array | null> {
  const db = await openVaultDb();
  const rec = await new Promise<StoredEncryptedRecord | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_DOCS, 'readonly');
    const req = tx.objectStore(STORE_DOCS).get(docId);
    req.onsuccess = () => resolve(req.result as StoredEncryptedRecord | undefined);
    req.onerror = () => reject(req.error);
  });
  return rec ? new Uint8Array(rec.payload) : null;
}

export async function readDecryptedDocument(docId: string): Promise<Uint8Array | null> {
  const payload = await readEncryptedPayload(docId);
  if (!payload) return null;
  const key = await getOrCreateAesKey();
  return decryptBytesWithKey(payload, key);
}

export async function hasLocalEncryptedDocument(docId: string): Promise<boolean> {
  const payload = await readEncryptedPayload(docId);
  return payload !== null;
}

export async function deleteLocalEncryptedDocument(docId: string): Promise<void> {
  const db = await openVaultDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_DOCS, 'readwrite');
    tx.objectStore(STORE_DOCS).delete(docId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function getLocalEncryptedStats(): Promise<LocalEncryptedStats> {
  try {
    const db = await openVaultDb();
    return await new Promise<LocalEncryptedStats>((resolve, reject) => {
      const tx = db.transaction(STORE_DOCS, 'readonly');
      const req = tx.objectStore(STORE_DOCS).getAll();
      req.onsuccess = () => {
        const rows = (req.result as StoredEncryptedRecord[]) || [];
        const totalBytes = rows.reduce((sum, item) => sum + (item.payload?.byteLength ?? 0), 0);
        resolve({ count: rows.length, totalBytes });
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return { count: 0, totalBytes: 0 };
  }
}

export function generateSalt(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function hashPin(pin: string, salt: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(`${salt}:${pin}:myfamilydoc`);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
