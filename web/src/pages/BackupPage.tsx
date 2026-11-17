import React, { useState } from 'react';
import {
  CheckCircle2,
  Cloud,
  CloudDownload,
  CloudOff,
  CloudUpload,
  Download,
  HardDrive,
  Lock,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react';
import {
  BackupStatusInfo,
  FamilyDocument,
  formatDateMillis,
  formatFileSize,
} from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { SyncStatusBadge } from '../components/SharedComponents';

interface BackupPageProps {
  documents: FamilyDocument[];
  backupStatus: BackupStatusInfo;
  isSyncing: boolean;
  canInstallPwa: boolean;
  isPwaInstalled: boolean;
  onManualBackup: () => Promise<void>;
  onRestoreFromCloud: () => Promise<void>;
  onToggleAutoBackup: (enabled: boolean) => Promise<void>;
  onChangeBackupInterval: (days: number) => void;
  onInstallPwa: () => Promise<void>;
  onOpenDocumentDetail: (doc: FamilyDocument) => void;
  strings: AppStrings;
}

export function BackupPage({
  documents,
  backupStatus,
  isSyncing,
  canInstallPwa,
  isPwaInstalled,
  onManualBackup,
  onRestoreFromCloud,
  onToggleAutoBackup,
  onChangeBackupInterval,
  onInstallPwa,
  onOpenDocumentDetail,
  strings,
}: BackupPageProps) {
  const [actionBusy, setActionBusy] = useState<'backup' | 'restore' | null>(null);

  const progressPct =
    backupStatus.totalDocs === 0
      ? 100
      : Math.min(100, Math.round((backupStatus.backedUpDocs / backupStatus.totalDocs) * 100));

  const runBackup = async () => {
    setActionBusy('backup');
    try {
      await onManualBackup();
    } finally {
      setActionBusy(null);
    }
  };

  const runRestore = async () => {
    setActionBusy('restore');
    try {
      await onRestoreFromCloud();
    } finally {
      setActionBusy(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 10,
        }}
      >
        <h1 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>{strings.backupCenterTitle}</h1>
        <SyncStatusBadge syncState={backupStatus.syncState} strings={strings} />
      </div>

      {!backupStatus.isOnline && (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            background: '#FEF3C7',
            color: '#92400E',
            borderRadius: 16,
            padding: 14,
            border: '1px solid rgba(245, 158, 11, 0.45)',
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          <CloudOff size={20} style={{ flexShrink: 0, marginTop: 2 }} />
          <span>{strings.offlineModeBanner}</span>
        </div>
      )}

      {/* Sync Overview Hero */}
      <div className="hero-card">
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <div style={{ fontSize: 13, color: '#BAE6FD', fontWeight: 600 }}>
              {strings.lastBackupLabel}{' '}
              {backupStatus.lastBackupAtMillis > 0
                ? formatDateMillis(backupStatus.lastBackupAtMillis)
                : strings.neverBackedUp}
            </div>
            <div style={{ fontSize: 26, fontWeight: 800, marginTop: 6 }}>
              {backupStatus.backedUpDocs} / {backupStatus.totalDocs}{' '}
              <span style={{ fontSize: 15, fontWeight: 600, color: '#BAE6FD' }}>
                {strings.cloudSyncedBadge}
              </span>
            </div>
          </div>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: 'rgba(56, 189, 248, 0.2)',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <ShieldCheck size={26} color="#38BDF8" />
          </div>
        </div>

        {/* Sync Progress Track */}
        <div
          style={{
            marginTop: 14,
            height: 8,
            borderRadius: 999,
            background: 'rgba(255, 255, 255, 0.16)',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${progressPct}%`,
              height: '100%',
              background: '#38BDF8',
              borderRadius: 999,
              transition: 'width 0.3s ease',
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: 10, marginTop: 18, flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn btn-sky"
            disabled={!backupStatus.isOnline || isSyncing || actionBusy !== null}
            onClick={() => void runBackup()}
            data-testid="backup-now-btn"
          >
            {actionBusy === 'backup' || isSyncing ? (
              <RefreshCw size={17} />
            ) : (
              <CloudUpload size={17} />
            )}
            <span>{strings.manualBackupNow}</span>
          </button>
          <button
            type="button"
            className="btn btn-ghost-light"
            disabled={!backupStatus.isOnline || isSyncing || actionBusy !== null}
            onClick={() => void runRestore()}
            data-testid="restore-cloud-btn"
          >
            <CloudDownload size={17} />
            <span>{strings.restoreFromCloud}</span>
          </button>
        </div>
      </div>

      {/* Storage Metrics */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 12,
        }}
      >
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: 'rgba(56, 189, 248, 0.16)',
              color: '#0284C7',
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
            }}
          >
            <HardDrive size={22} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
              {strings.storageUsageTitle}
            </div>
            <div style={{ fontSize: 20, fontWeight: 800 }}>
              {formatFileSize(backupStatus.totalStorageBytes)}
            </div>
          </div>
        </div>

        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: 'rgba(16, 185, 129, 0.16)',
              color: '#059669',
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
            }}
          >
            <Lock size={22} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
              {strings.encryptedLocalCount}
            </div>
            <div style={{ fontSize: 20, fontWeight: 800 }}>
              {backupStatus.localEncryptedDocs} ({formatFileSize(backupStatus.localVaultBytes)})
            </div>
          </div>
        </div>
      </div>

      {/* Auto-Backup Schedule Settings */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <div>
            <div style={{ fontWeight: 700, fontSize: 16 }}>{strings.autoBackupTitle}</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
              {strings.autoBackupSubtitle}
            </div>
          </div>
          <label style={{ display: 'inline-flex', alignItems: 'center', cursor: 'pointer', minHeight: 48 }}>
            <input
              type="checkbox"
              checked={backupStatus.autoBackupEnabled}
              onChange={(e) => void onToggleAutoBackup(e.target.checked)}
              style={{ width: 20, height: 20, accentColor: '#172554', cursor: 'pointer' }}
            />
          </label>
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
            paddingTop: 10,
            borderTop: '1px solid var(--border-color)',
          }}
        >
          <span style={{ fontSize: 14, fontWeight: 600 }}>{strings.backupFrequencyLabel}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              className={`filter-chip ${backupStatus.backupIntervalDays === 1 ? 'selected' : ''}`}
              onClick={() => onChangeBackupInterval(1)}
            >
              {strings.daily}
            </button>
            <button
              type="button"
              className={`filter-chip ${backupStatus.backupIntervalDays === 7 ? 'selected' : ''}`}
              onClick={() => onChangeBackupInterval(7)}
            >
              {strings.weekly}
            </button>
          </div>
        </div>
      </div>

      {/* Installable PWA Card */}
      {(canInstallPwa || isPwaInstalled) && (
        <div
          className="card"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <div style={{ fontWeight: 700, fontSize: 16 }}>{strings.installPwaTitle}</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
              {strings.installPwaSubtitle}
            </div>
          </div>
          {isPwaInstalled ? (
            <span className="pill" style={{ background: '#D1FAE5', color: '#065F46' }}>
              <CheckCircle2 size={15} />
              <span>{strings.pwaInstalledBadge}</span>
            </span>
          ) : (
            <button type="button" className="btn btn-primary" onClick={() => void onInstallPwa()}>
              <Download size={16} />
              <span>{strings.installPwaButton}</span>
            </button>
          )}
        </div>
      )}

      {/* Per-Document Sync Audit */}
      <div className="card">
        <div style={{ fontWeight: 800, fontSize: 16, marginBottom: 12 }}>
          {strings.backupHealthSection} ({documents.length})
        </div>
        {documents.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>{strings.emptyDocumentsTitle}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {documents.map((doc) => (
              <div
                key={doc.id}
                onClick={() => onOpenDocumentDetail(doc)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') onOpenDocumentDetail(doc);
                }}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 10,
                  padding: '10px 12px',
                  borderRadius: 12,
                  background: 'var(--bg-surface-variant)',
                  cursor: 'pointer',
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <div
                    style={{
                      fontWeight: 700,
                      fontSize: 14,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {doc.title}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {doc.memberName} • {formatFileSize(doc.fileSizeBytes)}
                  </div>
                </div>
                <span
                  className="pill"
                  style={{
                    background: doc.isBackedUp ? '#D1FAE5' : '#FEF3C7',
                    color: doc.isBackedUp ? '#065F46' : '#92400E',
                  }}
                >
                  <Cloud size={12} />
                  <span>{doc.isBackedUp ? strings.cloudSyncedBadge : strings.localOnlyBadge}</span>
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
