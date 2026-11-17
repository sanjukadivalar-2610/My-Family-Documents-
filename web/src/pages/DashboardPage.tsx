import React from 'react';
import {
  Camera,
  Copy,
  FileText,
  FolderKanban,
  HardDrive,
  Lock,
  ShieldCheck,
  Sparkles,
  Upload,
  UserPlus,
  Users,
} from 'lucide-react';
import {
  DOCUMENT_CATEGORIES,
  getLocalizedCategoryLabel,
} from '../domain/documentCategory';
import {
  BackupStatusInfo,
  FamilyAccount,
  FamilyDocument,
  FamilyMember,
  formatDateMillis,
  formatFileSize,
  getExpiryStatus,
  LanguageCode,
  UserProfile,
} from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import {
  CategoryIcon,
  DocumentCard,
  LanguageSwitcherPill,
  SyncStatusBadge,
} from '../components/SharedComponents';

interface DashboardPageProps {
  userProfile: UserProfile | null;
  family: FamilyAccount;
  members: FamilyMember[];
  documents: FamilyDocument[];
  backupStatus: BackupStatusInfo;
  isAdmin: boolean;
  language: LanguageCode;
  onChangeLanguage: (lang: LanguageCode) => void;
  onQuickUpload: () => void;
  onQuickScan: () => void;
  onQuickAddMember: () => void;
  onLoadSamples: () => void;
  onSelectCategory: (categoryKey: string) => void;
  onOpenDocuments: () => void;
  onOpenBackup: () => void;
  onOpenAdminPanel: () => void;
  onOpenDocumentDetail: (doc: FamilyDocument) => void;
  onCopyFamilyId: (code: string) => void;
  strings: AppStrings;
}

export function DashboardPage({
  userProfile,
  family,
  members,
  documents,
  backupStatus,
  isAdmin,
  language,
  onChangeLanguage,
  onQuickUpload,
  onQuickScan,
  onQuickAddMember,
  onLoadSamples,
  onSelectCategory,
  onOpenDocuments,
  onOpenBackup,
  onOpenAdminPanel,
  onOpenDocumentDetail,
  onCopyFamilyId,
  strings,
}: DashboardPageProps) {
  const expiringDocs = documents.filter((d) => {
    const st = getExpiryStatus(d.expiryDateMillis);
    return st === 'EXPIRING_SOON' || st === 'EXPIRED';
  });
  const activeCategories = new Set(documents.map((d) => d.category)).size;
  const recentDocs = documents.slice(0, 5);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {/* Banking Hero Card */}
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
              {strings.goodDayGreeting} {userProfile?.displayName || ''}
            </div>
            <h1 style={{ margin: '4px 0 0', fontSize: 26, fontWeight: 800 }}>
              {family.familyName}
            </h1>
          </div>
          <LanguageSwitcherPill language={language} onChange={onChangeLanguage} />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 14, flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn-ghost-light pill"
            style={{ padding: '7px 14px', cursor: 'pointer', border: '1px solid rgba(56, 189, 248, 0.5)' }}
            onClick={() => onCopyFamilyId(family.familyCode)}
            data-testid="copy-family-id"
          >
            <span>
              {strings.familyIdPrefix} {family.familyCode}
            </span>
            <Copy size={13} color="#38BDF8" />
          </button>
          <span
            className="pill"
            style={{
              background: 'rgba(56, 189, 248, 0.22)',
              color: '#38BDF8',
              fontWeight: 800,
              padding: '7px 12px',
            }}
          >
            {isAdmin ? strings.roleAdmin : strings.roleMember}
          </span>
        </div>

        <div style={{ display: 'flex', gap: 10, marginTop: 18, flexWrap: 'wrap' }}>
          <button type="button" className="btn btn-sky" onClick={onQuickUpload} data-testid="hero-upload-btn">
            <Upload size={17} />
            <span>{strings.quickUpload}</span>
          </button>
          <button type="button" className="btn btn-ghost-light" onClick={onQuickScan}>
            <Camera size={17} />
            <span>{strings.scanDocument}</span>
          </button>
          {isAdmin && (
            <button type="button" className="btn btn-ghost-light" onClick={onOpenAdminPanel}>
              <ShieldCheck size={17} />
              <span>{strings.adminConsoleButton}</span>
            </button>
          )}
        </div>
      </div>

      {/* Key Metrics Grid */}
      <div className="stats-grid">
        <div
          className="card"
          style={{ cursor: 'pointer' }}
          onClick={onOpenDocuments}
          role="button"
          tabIndex={0}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 12,
                background: 'rgba(56, 189, 248, 0.16)',
                color: '#0284C7',
                display: 'grid',
                placeItems: 'center',
              }}
            >
              <FileText size={20} />
            </div>
          </div>
          <div style={{ fontSize: 24, fontWeight: 800, marginTop: 10 }}>{documents.length}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
            {strings.totalDocuments}
          </div>
        </div>

        <div className="card">
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 12,
              background: 'rgba(23, 37, 84, 0.14)',
              color: '#172554',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Users size={20} />
          </div>
          <div style={{ fontSize: 24, fontWeight: 800, marginTop: 10 }}>{members.length}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
            {strings.totalMembers}
          </div>
        </div>

        <div
          className="card"
          style={{ cursor: 'pointer' }}
          onClick={onOpenDocuments}
          role="button"
          tabIndex={0}
        >
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 12,
              background: 'rgba(124, 58, 237, 0.14)',
              color: '#7C3AED',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <FolderKanban size={20} />
          </div>
          <div style={{ fontSize: 24, fontWeight: 800, marginTop: 10 }}>
            {activeCategories} / {DOCUMENT_CATEGORIES.length}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
            {strings.documentCategoriesCount}
          </div>
        </div>

        <div
          className="card"
          style={{ cursor: 'pointer' }}
          onClick={onOpenBackup}
          role="button"
          tabIndex={0}
        >
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 12,
              background: 'rgba(16, 185, 129, 0.14)',
              color: '#059669',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <HardDrive size={20} />
          </div>
          <div style={{ fontSize: 22, fontWeight: 800, marginTop: 10 }}>
            {formatFileSize(backupStatus.totalStorageBytes)}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>
            {strings.storageUsageTitle} • {backupStatus.localEncryptedDocs} <Lock size={10} />
          </div>
        </div>
      </div>

      {/* Backup Status Summary */}
      <div
        className="card"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 14,
          flexWrap: 'wrap',
          cursor: 'pointer',
        }}
        onClick={onOpenBackup}
        role="button"
        tabIndex={0}
      >
        <div>
          <div style={{ fontWeight: 700, fontSize: 16 }}>{strings.backupStatusTitle}</div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 3 }}>
            {strings.lastBackupLabel}{' '}
            {backupStatus.lastBackupAtMillis > 0
              ? formatDateMillis(backupStatus.lastBackupAtMillis)
              : strings.neverBackedUp}{' '}
            • {backupStatus.backedUpDocs}/{backupStatus.totalDocs}
          </div>
        </div>
        <SyncStatusBadge syncState={backupStatus.syncState} strings={strings} />
      </div>

      {/* Quick Actions */}
      <div>
        <h2 style={{ margin: '0 0 10px', fontSize: 18, fontWeight: 800 }}>{strings.quickActions}</h2>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <button type="button" className="btn btn-outline" onClick={onQuickUpload}>
            <Upload size={16} />
            <span>{strings.quickUpload}</span>
          </button>
          <button type="button" className="btn btn-outline" onClick={onQuickScan}>
            <Camera size={16} />
            <span>{strings.scanDocument}</span>
          </button>
          {isAdmin && (
            <button type="button" className="btn btn-outline" onClick={onQuickAddMember}>
              <UserPlus size={16} />
              <span>{strings.addMember}</span>
            </button>
          )}
          <button type="button" className="btn btn-outline" onClick={onLoadSamples}>
            <Sparkles size={16} color="#10B981" />
            <span>{strings.loadSampleDocs}</span>
          </button>
        </div>
      </div>

      {/* 12 Document Categories Grid */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>
            {strings.categoriesOverviewTitle}
          </h2>
          <button
            type="button"
            className="btn btn-outline"
            style={{ minHeight: 36, padding: '6px 12px', fontSize: 12 }}
            onClick={onOpenDocuments}
          >
            {strings.viewAll}
          </button>
        </div>
        <div className="categories-grid">
          {DOCUMENT_CATEGORIES.map((cat) => {
            const count = documents.filter((d) => d.category === cat.storageKey).length;
            return (
              <div
                key={cat.id}
                className="card"
                onClick={() => onSelectCategory(cat.storageKey)}
                role="button"
                tabIndex={0}
                style={{
                  padding: 12,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  cursor: 'pointer',
                }}
              >
                <div
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: `${cat.tintColor}20`,
                    display: 'grid',
                    placeItems: 'center',
                    flexShrink: 0,
                  }}
                >
                  <CategoryIcon iconKey={cat.iconKey} color={cat.tintColor} size={18} />
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: 700,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {getLocalizedCategoryLabel(cat, language)}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {count} {strings.navDocuments}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Expiry Alerts */}
      {expiringDocs.length > 0 && (
        <div>
          <h2 style={{ margin: '0 0 10px', fontSize: 18, fontWeight: 800 }}>
            {strings.expiryAlertTitle} ({expiringDocs.length})
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {expiringDocs.slice(0, 3).map((doc) => (
              <DocumentCard
                key={doc.id}
                document={doc}
                strings={strings}
                onClick={() => onOpenDocumentDetail(doc)}
              />
            ))}
          </div>
        </div>
      )}

      {/* Recently Uploaded Documents */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>{strings.recentDocuments}</h2>
          <button
            type="button"
            className="btn btn-outline"
            style={{ minHeight: 36, padding: '6px 12px', fontSize: 12 }}
            onClick={onOpenDocuments}
          >
            {strings.viewAll}
          </button>
        </div>

        {recentDocs.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 28 }}>
            <FileText size={36} color="#38BDF8" style={{ marginBottom: 8 }} />
            <div style={{ fontWeight: 700, fontSize: 16 }}>{strings.emptyDocumentsTitle}</div>
            <div style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 4 }}>
              {strings.emptyDocumentsSubtitle}
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {recentDocs.map((doc) => (
              <DocumentCard
                key={doc.id}
                document={doc}
                strings={strings}
                onClick={() => onOpenDocumentDetail(doc)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
