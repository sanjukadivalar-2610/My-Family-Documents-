import React from 'react';
import {
  Award,
  BadgeCheck,
  Car,
  Cloud,
  CloudOff,
  CloudUpload,
  CreditCard,
  FolderOpen,
  GraduationCap,
  HeartPulse,
  Home,
  Landmark,
  Languages,
  Plane,
  ReceiptText,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Clock,
  Vote,
  Lock,
} from 'lucide-react';
import { DocumentCategoryMeta, getCategoryByKey, getLocalizedCategoryLabel } from '../domain/documentCategory';
import { FamilyDocument, getExpiryStatus, LanguageCode, SyncState, formatFileSize } from '../domain/models';
import { AppStrings } from '../localization/appStrings';

export function CategoryIcon({
  iconKey,
  color,
  size = 20,
}: {
  iconKey: DocumentCategoryMeta['iconKey'];
  color?: string;
  size?: number;
}) {
  const props = { size, color, strokeWidth: 2 };
  switch (iconKey) {
    case 'badge':
      return <BadgeCheck {...props} />;
    case 'credit-card':
      return <CreditCard {...props} />;
    case 'vote':
      return <Vote {...props} />;
    case 'receipt':
      return <ReceiptText {...props} />;
    case 'car':
      return <Car {...props} />;
    case 'flight':
      return <Plane {...props} />;
    case 'school':
      return <GraduationCap {...props} />;
    case 'bank':
      return <Landmark {...props} />;
    case 'property':
      return <Home {...props} />;
    case 'medical':
      return <HeartPulse {...props} />;
    case 'award':
      return <Award {...props} />;
    case 'folder':
    default:
      return <FolderOpen {...props} />;
  }
}

export function LanguageSwitcherPill({
  language,
  onChange,
}: {
  language: LanguageCode;
  onChange: (lang: LanguageCode) => void;
}) {
  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-color)',
        borderRadius: 999,
        padding: 4,
      }}
      data-testid="language-switcher"
    >
      <Languages size={15} color="#38BDF8" style={{ marginLeft: 8, marginRight: 2 }} />
      <button
        type="button"
        onClick={() => onChange('en')}
        data-testid="lang-en"
        style={{
          border: 'none',
          borderRadius: 999,
          padding: '6px 12px',
          fontSize: 12,
          fontWeight: 700,
          cursor: 'pointer',
          background: language === 'en' ? '#172554' : 'transparent',
          color: language === 'en' ? '#FFFFFF' : 'var(--text-muted)',
        }}
      >
        EN
      </button>
      <button
        type="button"
        onClick={() => onChange('kn')}
        data-testid="lang-kn"
        style={{
          border: 'none',
          borderRadius: 999,
          padding: '6px 12px',
          fontSize: 12,
          fontWeight: 700,
          cursor: 'pointer',
          background: language === 'kn' ? '#172554' : 'transparent',
          color: language === 'kn' ? '#FFFFFF' : 'var(--text-muted)',
        }}
      >
        ಕನ್ನಡ
      </button>
    </div>
  );
}

export function SyncStatusBadge({
  syncState,
  strings,
}: {
  syncState: SyncState;
  strings: AppStrings;
}) {
  const map = {
    SYNCED: {
      label: strings.syncedStatus,
      bg: '#D1FAE5',
      fg: '#065F46',
      icon: <ShieldCheck size={14} />,
    },
    SYNCING: {
      label: strings.syncingStatus,
      bg: '#E0F2FE',
      fg: '#172554',
      icon: <RefreshCw size={14} />,
    },
    OFFLINE: {
      label: strings.offlineStatus,
      bg: '#FEF3C7',
      fg: '#92400E',
      icon: <CloudOff size={14} />,
    },
    ATTENTION_NEEDED: {
      label: strings.attentionStatus,
      bg: '#FEE2E2',
      fg: '#991B1B',
      icon: <CloudUpload size={14} />,
    },
  }[syncState];

  return (
    <span className="pill" style={{ background: map.bg, color: map.fg }}>
      {map.icon}
      <span>{map.label}</span>
    </span>
  );
}

export function ExpiryBadge({
  document,
  strings,
}: {
  document: FamilyDocument;
  strings: AppStrings;
}) {
  const status = getExpiryStatus(document.expiryDateMillis);
  if (status === 'NO_EXPIRY') return null;
  const cfg = {
    EXPIRED: { label: strings.expiredBadge, bg: '#FEE2E2', fg: '#991B1B', icon: <ShieldAlert size={12} /> },
    EXPIRING_SOON: { label: strings.expiringSoonBadge, bg: '#FEF3C7', fg: '#92400E', icon: <Clock size={12} /> },
    VALID: { label: strings.validBadge, bg: '#D1FAE5', fg: '#065F46', icon: <ShieldCheck size={12} /> },
  }[status];

  return (
    <span className="pill" style={{ background: cfg.bg, color: cfg.fg }}>
      {cfg.icon}
      <span>{cfg.label}</span>
    </span>
  );
}

export function MemberAvatar({
  name,
  photoBase64,
  size = 48,
}: {
  name: string;
  photoBase64?: string | null;
  size?: number;
}) {
  const initials = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');

  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: 'linear-gradient(135deg, #172554, #38BDF8)',
        color: '#FFFFFF',
        display: 'grid',
        placeItems: 'center',
        fontWeight: 700,
        fontSize: Math.round(size * 0.36),
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {photoBase64 ? (
        <img
          src={`data:image/jpeg;base64,${photoBase64}`}
          alt={name}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        initials || 'M'
      )}
    </div>
  );
}

export function DocumentCard({
  document,
  strings,
  onClick,
}: {
  document: FamilyDocument;
  strings: AppStrings;
  onClick: () => void;
}) {
  const cat = getCategoryByKey(document.category);

  return (
    <div
      className="card"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onClick();
      }}
      data-testid={`document-card-${document.id}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        cursor: 'pointer',
        padding: 14,
      }}
    >
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: 14,
          background: `${cat.tintColor}22`,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        <CategoryIcon iconKey={cat.iconKey} color={cat.tintColor} size={24} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span
            style={{
              fontWeight: 700,
              fontSize: 15,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {document.title}
          </span>
          {document.isSample && (
            <span
              className="pill"
              style={{ background: 'rgba(56, 189, 248, 0.2)', color: '#0284C7', fontSize: 10 }}
            >
              {strings.sampleDocBadge}
            </span>
          )}
        </div>
        <div style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 2 }}>
          {getLocalizedCategoryLabel(cat, strings.languageCode)} • {document.memberName}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
          <span
            className="pill"
            style={{ background: 'var(--bg-surface-variant)', color: 'var(--text-muted)' }}
          >
            <Lock size={11} color="#172554" />
            <span>
              {document.fileType.toUpperCase()} • {formatFileSize(document.fileSizeBytes)}
            </span>
          </span>
          <ExpiryBadge document={document} strings={strings} />
          <span
            className="pill"
            style={{
              background: document.isBackedUp ? '#D1FAE5' : 'var(--bg-surface-variant)',
              color: document.isBackedUp ? '#065F46' : 'var(--text-muted)',
            }}
          >
            <Cloud size={11} />
            <span>{document.isBackedUp ? strings.cloudSyncedBadge : strings.localOnlyBadge}</span>
          </span>
        </div>
      </div>
    </div>
  );
}
