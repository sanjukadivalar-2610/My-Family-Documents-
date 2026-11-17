import { FirebaseApp, getApps, initializeApp } from 'firebase/app';
import {
  Auth,
  connectAuthEmulator,
  getAuth,
  GoogleAuthProvider,
} from 'firebase/auth';
import {
  connectFirestoreEmulator,
  Firestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  getFirestore,
} from 'firebase/firestore';
import {
  connectStorageEmulator,
  FirebaseStorage,
  getStorage,
} from 'firebase/storage';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY?.trim() || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN?.trim() || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID?.trim() || '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET?.trim() || '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID?.trim() || '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID?.trim() || '',
};

export const FIRESTORE_DATABASE_ID =
  import.meta.env.VITE_FIREBASE_DATABASE_ID?.trim() ||
  'ai-studio-android-myfamily-31bcdd9d-0dee-46fe-8aa9-4f2cc590f319';

const USE_EMULATORS = import.meta.env.VITE_USE_FIREBASE_EMULATORS === 'true';

export const isFirebaseConfigured = Boolean(
  (firebaseConfig.apiKey && firebaseConfig.projectId) || USE_EMULATORS
);

let cachedApp: FirebaseApp | null = null;
let cachedAuth: Auth | null = null;
let cachedDb: Firestore | null = null;
let cachedStorage: FirebaseStorage | null = null;
let emulatorsConnected = false;

export interface FirebaseClients {
  app: FirebaseApp;
  auth: Auth;
  db: Firestore;
  storage: FirebaseStorage | null;
  googleProvider: GoogleAuthProvider;
}

export function getFirebaseClients(): FirebaseClients | null {
  if (!isFirebaseConfigured) return null;

  if (!cachedApp) {
    const resolvedConfig = USE_EMULATORS && !firebaseConfig.apiKey
      ? {
          apiKey: 'demo-emulator-key',
          authDomain: 'localhost',
          projectId: firebaseConfig.projectId || 'project-71ca2d4d-7b30-4952-b87',
          storageBucket: firebaseConfig.storageBucket || 'project-71ca2d4d-7b30-4952-b87.firebasestorage.app',
          appId: firebaseConfig.appId || '1:000000000000:web:emulator',
        }
      : firebaseConfig;

    cachedApp = getApps().length > 0 ? getApps()[0] : initializeApp(resolvedConfig);
    cachedAuth = getAuth(cachedApp);

    try {
      cachedDb = initializeFirestore(
        cachedApp,
        {
          localCache: persistentLocalCache({
            tabManager: persistentMultipleTabManager(),
          }),
        },
        FIRESTORE_DATABASE_ID
      );
    } catch {
      cachedDb = getFirestore(cachedApp, FIRESTORE_DATABASE_ID);
    }

    try {
      cachedStorage = resolvedConfig.storageBucket ? getStorage(cachedApp) : null;
    } catch {
      cachedStorage = null;
    }

    if (USE_EMULATORS && !emulatorsConnected && cachedAuth && cachedDb) {
      emulatorsConnected = true;
      connectAuthEmulator(cachedAuth, 'http://127.0.0.1:9099', { disableWarnings: true });
      connectFirestoreEmulator(cachedDb, '127.0.0.1', 8085);
      if (cachedStorage) {
        try {
          connectStorageEmulator(cachedStorage, '127.0.0.1', 9199);
        } catch {
          // Storage emulator optional
        }
      }
    }
  }

  return {
    app: cachedApp!,
    auth: cachedAuth!,
    db: cachedDb!,
    storage: cachedStorage,
    googleProvider: new GoogleAuthProvider(),
  };
}
