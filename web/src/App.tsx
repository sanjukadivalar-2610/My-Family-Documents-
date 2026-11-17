import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, signOut, User } from 'firebase/auth';
import { CloudUpload, FileText, Home, Settings, Shield, Users } from 'lucide-react';
import { getCategoryByKey } from './domain/documentCategory';
import {
  BackupStatusInfo,
  FamilyAccount,
  FamilyDocument,
  FamilyMember,
  getDaysUntilExpiry,
  getExpiryStatus,
  LanguageCode,
  SecuritySettings,
  SyncState,
  ThemeMode,
  UserProfile,
} from './domain/models';
import { stringsForLanguage } from './localization/appStrings';
import { getFirebaseClients, isFirebaseConfigured } from './services/firebaseClient';
import { FamilyVaultRepository } from './services/familyVaultRepository';
import {
  deleteLocalEncryptedDocument,
  getLocalEncryptedStats,
  hasLocalEncryptedDocument,
  LocalEncryptedStats,
  readDecryptedDocument,
  readEncryptedPayload,
  saveEncryptedDocument,
  writeRawEncryptedPayload,
} from './services/cryptoVault';
import {
  createPinSettings,
  loadSecuritySettings,
  saveSecuritySettings,
  setThemeAttribute,
  updatePreferredLanguage,
  verifyStoredPin,
} from './services/preferences';
import {
  computeSampleTimestamps,
  getSampleTemplates,
  renderSampleBytes,
  SampleDocumentTemplate,
} from './services/sampleDocumentFactory';
import { AuthView } from './components/AuthView';
import { OnboardingView } from './components/OnboardingView';
import { PinLockModal } from './components/PinLockModal';
import { DashboardPage } from './pages/DashboardPage';
import { FamilyPage } from './pages/FamilyPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { BackupPage } from './pages/BackupPage';
import { AdminPanelPage } from './pages/AdminPanelPage';
import { SettingsPage } from './pages/SettingsPage';

type TabKey = 'home' | 'family' | 'documents' | 'backup' | 'settings';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function generateDocId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  const hex = Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 12);
  return `doc_${hex}`;
}

export function App() {
  const clients = useMemo(() => getFirebaseClients(), []);
  const repository = useMemo(
    () => (clients ? new FamilyVaultRepository(clients.db, clients.auth, clients.storage) : null),
    [clients]
  );

  const [settings, setSettings] = useState<SecuritySettings>(() => {
    const initial = loadSecuritySettings();
    setThemeAttribute(initial.themeMode);
    return initial;
  });

  const strings = useMemo(() => stringsForLanguage(settings.languageCode), [settings.languageCode]);

  const [isOnline, setIsOnline] = useState<boolean>(() =>
    typeof navigator !== 'undefined' ? navigator.onLine : true
  );
  const [deferredInstallPrompt, setDeferredInstallPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isPwaInstalled, setIsPwaInstalled] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia?.('(display-mode: standalone)').matches ?? false;
  });

  const [authReady, setAuthReady] = useState(!clients);
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [familyAccount, setFamilyAccount] = useState<FamilyAccount | null>(null);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [documents, setDocuments] = useState<FamilyDocument[]>([]);
  const [localStats, setLocalStats] = useState<LocalEncryptedStats>({ count: 0, totalBytes: 0 });

  const [pinUnlocked, setPinUnlocked] = useState<boolean>(() => !settings.pinEnabled);
  const [showPinSetupModal, setShowPinSetupModal] = useState(false);

  const [activeTab, setActiveTab] = useState<TabKey>('home');
  const [showAdminPanel, setShowAdminPanel] = useState(false);

  const [docCategoryFilter, setDocCategoryFilter] = useState<string | null>(null);
  const [docMemberFilter, setDocMemberFilter] = useState<string | null>(null);
  const [openUploadInitially, setOpenUploadInitially] = useState(false);
  const [openScannerInitially, setOpenScannerInitially] = useState(false);
  const [openAddMemberInitially, setOpenAddMemberInitially] = useState(false);
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(null);

  const [onboardingBusy, setOnboardingBusy] = useState(false);
  const [onboardingError, setOnboardingError] = useState<string | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
  }, []);

  useEffect(() => {
    if (!toastMsg) return;
    const t = setTimeout(() => setToastMsg(null), 3600);
    return () => clearTimeout(t);
  }, [toastMsg]);

  const refreshLocalStats = useCallback(async () => {
    const st = await getLocalEncryptedStats();
    setLocalStats(st);
  }, []);

  // Online/offline listeners
  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => setIsOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  // System theme watcher
  useEffect(() => {
    setThemeAttribute(settings.themeMode);
    if (settings.themeMode !== 'system' || !window.matchMedia) return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => setThemeAttribute('system');
    mq.addEventListener?.('change', handler);
    return () => mq.removeEventListener?.('change', handler);
  }, [settings.themeMode]);

  // PWA install prompt listener
  useEffect(() => {
    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredInstallPrompt(e as BeforeInstallPromptEvent);
    };
    const handleAppInstalled = () => {
      setIsPwaInstalled(true);
      setDeferredInstallPrompt(null);
    };
    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    window.addEventListener('appinstalled', handleAppInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  // Firebase Auth state & User Profile subscription
  useEffect(() => {
    if (!clients || !repository) {
      setAuthReady(true);
      return;
    }
    let unsubProfile: (() => void) | null = null;
    const unsubAuth = onAuthStateChanged(clients.auth, (user) => {
      setFirebaseUser(user);
      setAuthReady(true);
      if (unsubProfile) {
        unsubProfile();
        unsubProfile = null;
      }
      if (!user) {
        setUserProfile(null);
        setFamilyAccount(null);
        setMembers([]);
        setDocuments([]);
        setShowAdminPanel(false);
        return;
      }

      const currentPrefs = loadSecuritySettings();
      if (currentPrefs.pinEnabled) {
        setPinUnlocked(false);
      }
      void repository.ensureUserProfile(currentPrefs.languageCode).catch((err) => {
        showToast(err instanceof Error ? err.message : 'Profile init failed');
      });

      unsubProfile = repository.observeUserProfile(
        user.uid,
        (profile) => {
          setUserProfile(profile);
        },
        (err) => showToast(err.message)
      );
    });

    return () => {
      unsubAuth();
      if (unsubProfile) unsubProfile();
    };
  }, [clients, repository, showToast]);

  // Family, Members & Documents listeners
  useEffect(() => {
    if (!repository || !firebaseUser || !userProfile?.familyId) {
      setFamilyAccount(null);
      setMembers([]);
      setDocuments([]);
      return;
    }

    const familyId = userProfile.familyId;
    const uid = firebaseUser.uid;

    const unsubFamily = repository.observeFamilyAccount(
      familyId,
      (fam) => setFamilyAccount(fam),
      (err) => showToast(err.message)
    );
    const unsubMembers = repository.observeFamilyMembers(
      familyId,
      uid,
      (list) => setMembers(list),
      (err) => showToast(err.message)
    );
    const unsubDocs = repository.observePermittedDocuments(
      familyId,
      uid,
      (docs) => {
        setDocuments(docs);
        void refreshLocalStats();
      },
      (err) => showToast(err.message)
    );

    void refreshLocalStats();

    return () => {
      unsubFamily();
      unsubMembers();
      unsubDocs();
    };
  }, [repository, firebaseUser, userProfile?.familyId, refreshLocalStats, showToast]);

  // Keep member.memberUids aligned if admin
  useEffect(() => {
    if (!repository || !familyAccount || members.length === 0) return;
    if (familyAccount.adminUid === firebaseUser?.uid) {
      void repository.syncMemberUidsIfAdmin(familyAccount, members);
    }
  }, [repository, familyAccount, members, firebaseUser?.uid]);

  const isAdmin = Boolean(
    familyAccount && firebaseUser && familyAccount.adminUid === firebaseUser.uid
  );

  const backupStatus: BackupStatusInfo = useMemo(() => {
    const totalDocs = documents.length;
    const backedUpDocs = documents.filter((d) => d.isBackedUp).length;
    const totalStorageBytes = documents.reduce((sum, d) => sum + d.fileSizeBytes, 0);
    const lastBackupAtMillis = Math.max(
      familyAccount?.lastBackupAtMillis ?? 0,
      settings.lastLocalBackupMillis
    );

    let syncState: SyncState;
    if (isSyncing) {
      syncState = 'SYNCING';
    } else if (!isOnline) {
      syncState = 'OFFLINE';
    } else if (totalDocs > 0 && backedUpDocs < totalDocs) {
      syncState = 'ATTENTION_NEEDED';
    } else {
      syncState = 'SYNCED';
    }

    return {
      syncState,
      isOnline,
      totalDocs,
      backedUpDocs,
      localEncryptedDocs: localStats.count,
      totalStorageBytes,
      localVaultBytes: localStats.totalBytes,
      lastBackupAtMillis,
      autoBackupEnabled: familyAccount?.autoBackupEnabled ?? settings.autoBackupEnabled,
      backupIntervalDays: settings.backupIntervalDays,
    };
  }, [
    documents,
    familyAccount,
    isOnline,
    isSyncing,
    localStats,
    settings.autoBackupEnabled,
    settings.backupIntervalDays,
    settings.lastLocalBackupMillis,
  ]);

  const handleLanguageChange = (lang: LanguageCode) => {
    const next = updatePreferredLanguage(settings, lang);
    setSettings(next);
    if (repository && firebaseUser) {
      void repository.updateUserLanguage(lang).catch(() => {});
    }
  };

  const handleThemeChange = (mode: ThemeMode) => {
    const next: SecuritySettings = { ...settings, themeMode: mode };
    saveSecuritySettings(next);
    setSettings(next);
    setThemeAttribute(mode);
  };

  const handleInstallPwa = async () => {
    if (!deferredInstallPrompt) return;
    await deferredInstallPrompt.prompt();
    const res = await deferredInstallPrompt.userChoice;
    if (res.outcome === 'accepted') {
      setIsPwaInstalled(true);
      setDeferredInstallPrompt(null);
    }
  };

  const handleCreateFamily = async (familyName: string, creatorName: string) => {
    if (!repository) return;
    setOnboardingBusy(true);
    setOnboardingError(null);
    try {
      await repository.createFamily(familyName, creatorName, settings.languageCode);
    } catch (err) {
      setOnboardingError(err instanceof Error ? err.message : 'Unable to create family.');
    } finally {
      setOnboardingBusy(false);
    }
  };

  const handleJoinFamily = async (familyCode: string, memberName: string) => {
    if (!repository) return;
    setOnboardingBusy(true);
    setOnboardingError(null);
    try {
      await repository.joinFamilyByCode(familyCode, memberName, settings.languageCode);
    } catch (err) {
      setOnboardingError(err instanceof Error ? err.message : 'Unable to join family.');
    } finally {
      setOnboardingBusy(false);
    }
  };

  const handleUploadDocument = async (params: {
    file: File;
    title: string;
    categoryKey: string;
    member: FamilyMember | null;
    maskedId: string;
    notes: string;
    issueDateMillis: number;
    expiryDateMillis: number;
  }) => {
    if (!repository || !familyAccount || !firebaseUser) return;
    if (params.file.size > 25 * 1024 * 1024) {
      throw new Error('Maximum file size is 25 MB.');
    }
    const ext = params.file.name.split('.').pop()?.toLowerCase() || '';
    const fileType: 'pdf' | 'jpg' | 'png' =
      ext === 'pdf' ? 'pdf' : ext === 'png' ? 'png' : 'jpg';

    const docId = generateDocId();
    const plainBytes = new Uint8Array(await params.file.arrayBuffer());
    await saveEncryptedDocument(docId, plainBytes);

    const allowedUids = Array.from(
      new Set(
        [
          familyAccount.adminUid,
          firebaseUser.uid,
          params.member?.linkedUserId || '',
        ].filter(Boolean)
      )
    );

    await repository.saveNewDocumentMetadata({
      family: familyAccount,
      docId,
      title: params.title,
      category: params.categoryKey,
      memberId: params.member?.id || '',
      memberName: params.member?.name || userProfile?.displayName || 'Family',
      allowedUids,
      fileType,
      fileName: params.file.name,
      fileSizeBytes: plainBytes.byteLength,
      isEncrypted: true,
      isBackedUp: false,
      isSample: false,
      documentNumberMasked: params.maskedId || null,
      notes: params.notes || null,
      issueDateMillis: params.issueDateMillis,
      expiryDateMillis: params.expiryDateMillis,
    });

    if (settings.autoBackupEnabled && familyAccount.autoBackupEnabled && isOnline) {
      try {
        const enc = await readEncryptedPayload(docId);
        if (enc) {
          await repository.uploadEncryptedPayloadToStorage(familyAccount.familyId, docId, enc);
        }
      } catch {
        // Non-fatal: local encrypted doc + metadata already persisted
      }
    }

    await refreshLocalStats();
    showToast(strings.documentSaved);
  };

  const handleLoadSamples = async () => {
    if (!repository || !familyAccount || !firebaseUser) return;
    const owner = members[0] || null;
    const ownerName = owner?.name || userProfile?.displayName || 'Demo Member';
    const templates = getSampleTemplates(ownerName);
    const now = Date.now();

    for (const tpl of templates) {
      const docId = generateDocId();
      const rawBytes = await renderSampleBytes(tpl, ownerName);
      await saveEncryptedDocument(docId, rawBytes);
      const { issueDateMillis, expiryDateMillis } = computeSampleTimestamps(tpl, now);

      const allowedUids = Array.from(
        new Set([familyAccount.adminUid, firebaseUser.uid, owner?.linkedUserId || ''].filter(Boolean))
      );

      await repository.saveNewDocumentMetadata({
        family: familyAccount,
        docId,
        title: tpl.title,
        category: tpl.categoryKey,
        memberId: owner?.id || '',
        memberName: ownerName,
        allowedUids,
        fileType: tpl.fileType,
        fileName: tpl.fileName,
        fileSizeBytes: rawBytes.byteLength,
        isEncrypted: true,
        isBackedUp: false,
        isSample: true,
        documentNumberMasked: tpl.maskedId,
        notes: tpl.notes,
        issueDateMillis,
        expiryDateMillis,
      });

      if (settings.autoBackupEnabled && familyAccount.autoBackupEnabled && isOnline) {
        try {
          const enc = await readEncryptedPayload(docId);
          if (enc) {
            await repository.uploadEncryptedPayloadToStorage(familyAccount.familyId, docId, enc);
          }
        } catch {
          // Non-fatal
        }
      }
    }

    await refreshLocalStats();
    showToast(strings.documentSaved);
  };

  const handleLoadDecryptedBytes = useCallback(
    async (doc: FamilyDocument): Promise<Uint8Array | null> => {
      try {
        const local = await readDecryptedDocument(doc.id);
        if (local) return local;
      } catch {
        // Ignore corrupt/mismatched local key and try cloud/sample
      }

      if (repository && doc.storagePath && isOnline) {
        try {
          const encBytes = await repository.downloadEncryptedPayloadFromStorage(doc.storagePath);
          await writeRawEncryptedPayload(doc.id, encBytes);
          const dec = await readDecryptedDocument(doc.id);
          if (dec) return dec;
        } catch {
          // May have been encrypted with another device key; fall through
        }
      }

      if (doc.isSample) {
        const cat = getCategoryByKey(doc.category);
        const tpl: SampleDocumentTemplate = {
          title: doc.title,
          categoryKey: cat.storageKey,
          fileType: doc.fileType === 'pdf' ? 'pdf' : 'png',
          fileName: doc.fileName || 'sample.pdf',
          maskedId: doc.documentNumberMasked || 'DEMO-0000',
          notes: doc.notes || 'Synthetic sample document.',
          issueOffsetDays: 0,
          expiryOffsetDays: 0,
        };
        const regenerated = await renderSampleBytes(tpl, doc.memberName || 'Family');
        await saveEncryptedDocument(doc.id, regenerated);
        await refreshLocalStats();
        return regenerated;
      }

      return null;
    },
    [repository, isOnline, refreshLocalStats]
  );

  const handleManualBackup = async () => {
    if (!repository || !familyAccount) return;
    setIsSyncing(true);
    let uploadedCount = 0;
    try {
      for (const doc of documents) {
        const enc = await readEncryptedPayload(doc.id);
        if (enc) {
          try {
            await repository.uploadEncryptedPayloadToStorage(familyAccount.familyId, doc.id, enc);
            uploadedCount++;
          } catch {
            // Continue syncing remaining files
          }
        }
      }
      await repository.markBackupTimestamp(familyAccount.familyId);
      const nextPrefs: SecuritySettings = { ...settings, lastLocalBackupMillis: Date.now() };
      saveSecuritySettings(nextPrefs);
      setSettings(nextPrefs);
      showToast(`${strings.backupCompletedMsg} (${uploadedCount}/${documents.length})`);
    } finally {
      setIsSyncing(false);
    }
  };

  const handleRestoreFromCloud = async () => {
    if (!repository) return;
    setIsSyncing(true);
    let restored = 0;
    try {
      for (const doc of documents) {
        const exists = await hasLocalEncryptedDocument(doc.id);
        if (!exists) {
          if (doc.storagePath) {
            try {
              const enc = await repository.downloadEncryptedPayloadFromStorage(doc.storagePath);
              await writeRawEncryptedPayload(doc.id, enc);
              restored++;
            } catch {
              // Skip if unavailable
            }
          } else if (doc.isSample) {
            await handleLoadDecryptedBytes(doc);
            restored++;
          }
        }
      }
      await refreshLocalStats();
      showToast(`${strings.restoreCompletedMsg} (${restored})`);
    } finally {
      setIsSyncing(false);
    }
  };

  const handleCheckExpiriesNow = async () => {
    const expiring = documents.filter((d) => {
      const st = getExpiryStatus(d.expiryDateMillis);
      return st === 'EXPIRED' || st === 'EXPIRING_SOON';
    });

    if (expiring.length === 0) {
      showToast(`${strings.validBadge} (0 ${strings.expiringSoonCount})`);
      return;
    }

    const first = expiring[0];
    const days = getDaysUntilExpiry(first.expiryDateMillis);
    const summary =
      days < 0
        ? `${first.title}: ${strings.expiredBadge}`
        : `${first.title}: ${days}d (${expiring.length})`;

    if (typeof Notification !== 'undefined') {
      try {
        if (Notification.permission === 'default') {
          await Notification.requestPermission();
        }
        if (Notification.permission === 'granted') {
          new Notification(strings.expiryAlertTitle, {
            body: summary,
            icon: '/icons/icon-192.png',
          });
        }
      } catch {
        // Browser may disallow Notification constructor in certain contexts
      }
    }
    showToast(`${strings.expiryAlertTitle}: ${summary}`);
  };

  const handleCopyFamilyId = async (code: string) => {
    try {
      await navigator.clipboard?.writeText(code);
    } catch {
      // Fallback ignored
    }
    showToast(strings.copiedFamilyId);
  };

  const handleLogout = async () => {
    if (!clients) return;
    await signOut(clients.auth);
    setPinUnlocked(!settings.pinEnabled);
  };

  // Loading auth state
  if (!authReady) {
    return (
      <div style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center' }}>
        <div className="spinner" />
      </div>
    );
  }

  // Unauthenticated or unconfigured Firebase -> Auth screen
  if (!firebaseUser || !isFirebaseConfigured) {
    return (
      <AuthView
        auth={clients?.auth || null}
        googleProvider={clients?.googleProvider || null}
        firebaseConfigured={isFirebaseConfigured}
        language={settings.languageCode}
        onChangeLanguage={handleLanguageChange}
        strings={strings}
      />
    );
  }

  // PIN Lock Gate
  if (settings.pinEnabled && !pinUnlocked) {
    return (
      <PinLockModal
        mode="unlock"
        verifyPin={(pin) => verifyStoredPin(settings, pin)}
        onSuccess={() => setPinUnlocked(true)}
        strings={strings}
      />
    );
  }

  // Wait for profile before deciding onboarding vs main shell
  if (!userProfile) {
    return (
      <div style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center' }}>
        <div className="spinner" />
      </div>
    );
  }

  // Family onboarding (Create / Join with Family ID)
  if (!userProfile.familyId) {
    return (
      <OnboardingView
        defaultName={userProfile.displayName || firebaseUser.displayName || ''}
        language={settings.languageCode}
        isBusy={onboardingBusy}
        errorMessage={onboardingError}
        onChangeLanguage={handleLanguageChange}
        onCreateFamily={handleCreateFamily}
        onJoinFamily={handleJoinFamily}
        onSignOut={() => void handleLogout()}
        strings={strings}
      />
    );
  }

  // Wait for family account snapshot
  if (!familyAccount) {
    return (
      <div style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center' }}>
        <div className="spinner" />
      </div>
    );
  }

  const navItems: Array<{ key: TabKey; label: string; icon: React.ReactNode }> = [
    { key: 'home', label: strings.navHome, icon: <Home size={20} /> },
    { key: 'family', label: strings.navFamily, icon: <Users size={20} /> },
    { key: 'documents', label: strings.navDocuments, icon: <FileText size={20} /> },
    { key: 'backup', label: strings.navBackup, icon: <CloudUpload size={20} /> },
    { key: 'settings', label: strings.navSettings, icon: <Settings size={20} /> },
  ];

  const selectTab = (tab: TabKey) => {
    setShowAdminPanel(false);
    setActiveTab(tab);
  };

  return (
    <div className="app-shell">
      {/* Desktop & Tablet Side Rail */}
      <aside className="side-rail" aria-label="Primary Navigation">
        <div className="rail-brand">
          <div className="rail-logo">
            <Shield size={22} />
          </div>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 800, fontSize: 15, lineHeight: 1.2 }}>{strings.appName}</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{familyAccount.familyCode}</div>
          </div>
        </div>

        {navItems.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`rail-btn ${!showAdminPanel && activeTab === item.key ? 'active' : ''}`}
            onClick={() => selectTab(item.key)}
            data-testid={`nav-rail-${item.key}`}
          >
            {item.icon}
            <span>{item.label}</span>
          </button>
        ))}

        {isAdmin && (
          <button
            type="button"
            className={`rail-btn ${showAdminPanel ? 'active' : ''}`}
            onClick={() => setShowAdminPanel(true)}
            style={{ marginTop: 'auto' }}
            data-testid="nav-rail-admin"
          >
            <Shield size={20} />
            <span>{strings.adminConsoleButton}</span>
          </button>
        )}
      </aside>

      {/* Main Responsive Content Pane */}
      <main className="main-pane">
        <div className="content-container">
          {showAdminPanel && isAdmin ? (
            <AdminPanelPage
              family={familyAccount}
              members={members}
              documents={documents}
              backupStatus={backupStatus}
              onBack={() => setShowAdminPanel(false)}
              onRenameFamily={async (newName) => {
                await repository?.renameFamily(familyAccount, newName);
                showToast(strings.save);
              }}
              onCopyFamilyId={(code) => void handleCopyFamilyId(code)}
              onSaveMember={async (p) => {
                await repository?.addOrUpdateMember({
                  family: familyAccount,
                  existingMember: p.existing,
                  name: p.name,
                  relation: p.relation,
                  role: p.role,
                  email: p.email,
                  photoBase64: p.photoBase64,
                });
              }}
              onDeleteMember={async (id) => {
                await repository?.deleteMember(familyAccount.familyId, id);
              }}
              onUpdatePermissions={async (doc, allowed) => {
                await repository?.updateDocumentPermissions(familyAccount, doc, allowed);
                showToast(strings.permissionsSaved);
              }}
              onManualBackup={handleManualBackup}
              strings={strings}
            />
          ) : activeTab === 'home' ? (
            <DashboardPage
              userProfile={userProfile}
              family={familyAccount}
              members={members}
              documents={documents}
              backupStatus={backupStatus}
              isAdmin={isAdmin}
              language={settings.languageCode}
              onChangeLanguage={handleLanguageChange}
              onQuickUpload={() => {
                setOpenUploadInitially(true);
                selectTab('documents');
              }}
              onQuickScan={() => {
                setOpenScannerInitially(true);
                selectTab('documents');
              }}
              onQuickAddMember={() => {
                setOpenAddMemberInitially(true);
                selectTab('family');
              }}
              onLoadSamples={() => void handleLoadSamples()}
              onSelectCategory={(catKey) => {
                setDocCategoryFilter(catKey);
                selectTab('documents');
              }}
              onOpenDocuments={() => selectTab('documents')}
              onOpenBackup={() => selectTab('backup')}
              onOpenAdminPanel={() => setShowAdminPanel(true)}
              onOpenDocumentDetail={(doc) => {
                setSelectedDocumentId(doc.id);
                selectTab('documents');
              }}
              onCopyFamilyId={(code) => void handleCopyFamilyId(code)}
              strings={strings}
            />
          ) : activeTab === 'family' ? (
            <FamilyPage
              family={familyAccount}
              members={members}
              documents={documents}
              currentUserId={firebaseUser.uid}
              isAdmin={isAdmin}
              openAddInitially={openAddMemberInitially}
              onConsumeInitialAdd={() => setOpenAddMemberInitially(false)}
              onSaveMember={async (p) => {
                await repository?.addOrUpdateMember({
                  family: familyAccount,
                  existingMember: p.existing,
                  name: p.name,
                  relation: p.relation,
                  role: p.role,
                  email: p.email,
                  photoBase64: p.photoBase64,
                });
              }}
              onDeleteMember={async (id) => {
                await repository?.deleteMember(familyAccount.familyId, id);
              }}
              onOpenMemberDocuments={(memberId) => {
                setDocMemberFilter(memberId);
                selectTab('documents');
              }}
              onOpenAdminPanel={() => setShowAdminPanel(true)}
              strings={strings}
            />
          ) : activeTab === 'documents' ? (
            <DocumentsPage
              family={familyAccount}
              documents={documents}
              members={members}
              isAdmin={isAdmin}
              initialCategoryFilter={docCategoryFilter}
              initialMemberFilter={docMemberFilter}
              openUploadInitially={openUploadInitially}
              openScannerInitially={openScannerInitially}
              selectedDocumentId={selectedDocumentId}
              onConsumeInitialFilters={() => {
                setDocCategoryFilter(null);
                setDocMemberFilter(null);
                setOpenUploadInitially(false);
                setOpenScannerInitially(false);
              }}
              onSelectDocumentId={setSelectedDocumentId}
              onUploadDocument={handleUploadDocument}
              onLoadSamples={handleLoadSamples}
              onLoadDecryptedBytes={handleLoadDecryptedBytes}
              onUpdateDocument={async (p) => {
                await repository?.renameOrUpdateDates({
                  familyId: familyAccount.familyId,
                  docId: p.doc.id,
                  newTitle: p.newTitle,
                  category: p.categoryKey,
                  issueDateMillis: p.issueDateMillis,
                  expiryDateMillis: p.expiryDateMillis,
                  notes: p.notes,
                });
                showToast(strings.documentSaved);
              }}
              onUpdatePermissions={async (doc, allowed) => {
                await repository?.updateDocumentPermissions(familyAccount, doc, allowed);
                showToast(strings.permissionsSaved);
              }}
              onDeleteDocument={async (doc) => {
                await deleteLocalEncryptedDocument(doc.id);
                await repository?.deleteDocument(familyAccount.familyId, doc.id, doc.storagePath);
                await refreshLocalStats();
              }}
              onShowToast={showToast}
              strings={strings}
            />
          ) : activeTab === 'backup' ? (
            <BackupPage
              documents={documents}
              backupStatus={backupStatus}
              isSyncing={isSyncing}
              canInstallPwa={Boolean(deferredInstallPrompt)}
              isPwaInstalled={isPwaInstalled}
              onManualBackup={handleManualBackup}
              onRestoreFromCloud={handleRestoreFromCloud}
              onToggleAutoBackup={async (enabled) => {
                const nextPrefs: SecuritySettings = { ...settings, autoBackupEnabled: enabled };
                saveSecuritySettings(nextPrefs);
                setSettings(nextPrefs);
                if (repository && isAdmin) {
                  await repository.updateFamilyAutoBackup(familyAccount.familyId, enabled);
                }
              }}
              onChangeBackupInterval={(days) => {
                const nextPrefs: SecuritySettings = { ...settings, backupIntervalDays: days };
                saveSecuritySettings(nextPrefs);
                setSettings(nextPrefs);
              }}
              onInstallPwa={handleInstallPwa}
              onOpenDocumentDetail={(doc) => {
                setSelectedDocumentId(doc.id);
                selectTab('documents');
              }}
              strings={strings}
            />
          ) : (
            <SettingsPage
              userProfile={userProfile}
              settings={settings}
              isAdmin={isAdmin}
              canInstallPwa={Boolean(deferredInstallPrompt)}
              isPwaInstalled={isPwaInstalled}
              onChangeLanguage={handleLanguageChange}
              onChangeTheme={handleThemeChange}
              onOpenSetupPin={() => setShowPinSetupModal(true)}
              onDisablePin={() => {
                const nextPrefs: SecuritySettings = {
                  ...settings,
                  pinEnabled: false,
                  pinHash: '',
                  pinSalt: '',
                };
                saveSecuritySettings(nextPrefs);
                setSettings(nextPrefs);
              }}
              onToggleExpiryReminders={(enabled) => {
                const nextPrefs: SecuritySettings = {
                  ...settings,
                  expiryRemindersEnabled: enabled,
                };
                saveSecuritySettings(nextPrefs);
                setSettings(nextPrefs);
              }}
              onCheckExpiriesNow={() => void handleCheckExpiriesNow()}
              onOpenBackup={() => selectTab('backup')}
              onOpenAdminPanel={() => setShowAdminPanel(true)}
              onInstallPwa={handleInstallPwa}
              onLogout={handleLogout}
              strings={strings}
            />
          )}
        </div>
      </main>

      {/* Mobile Bottom Navigation */}
      <nav className="bottom-nav" aria-label="Bottom Navigation">
        {navItems.map((item) => {
          const isActive = !showAdminPanel && activeTab === item.key;
          return (
            <button
              key={item.key}
              type="button"
              className={`bottom-nav-btn ${isActive ? 'active' : ''}`}
              onClick={() => selectTab(item.key)}
              data-testid={`nav-bottom-${item.key}`}
            >
              <span className="bottom-nav-pill">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Optional Setup / Change 4-Digit PIN Modal */}
      {showPinSetupModal && (
        <PinLockModal
          mode="setup"
          verifyPin={async () => true}
          onSuccess={(newPin) => {
            void createPinSettings(settings, newPin).then((updated) => {
              setSettings(updated);
              setPinUnlocked(true);
              setShowPinSetupModal(false);
              showToast(strings.appPinLock);
            });
          }}
          onCancel={() => setShowPinSetupModal(false)}
          strings={strings}
        />
      )}

      {toastMsg && (
        <div className="toast-banner" role="status" aria-live="polite">
          {toastMsg}
        </div>
      )}
    </div>
  );
}

export default App;
