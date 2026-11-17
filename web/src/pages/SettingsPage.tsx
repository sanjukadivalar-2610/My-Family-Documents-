import React, { useState } from 'react';
import {
  BellRing,
  CheckCircle2,
  Cloud,
  Download,
  Info,
  KeyRound,
  LogOut,
  Monitor,
  Moon,
  Shield,
  ShieldCheck,
  Sun,
} from 'lucide-react';
import { LanguageCode, SecuritySettings, ThemeMode, UserProfile } from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { LanguageSwitcherPill } from '../components/SharedComponents';

interface SettingsPageProps {
  userProfile: UserProfile | null;
  settings: SecuritySettings;
  isAdmin: boolean;
  canInstallPwa: boolean;
  isPwaInstalled: boolean;
  onChangeLanguage: (lang: LanguageCode) => void;
  onChangeTheme: (mode: ThemeMode) => void;
  onOpenSetupPin: () => void;
  onDisablePin: () => void;
  onToggleExpiryReminders: (enabled: boolean) => void;
  onCheckExpiriesNow: () => void;
  onOpenBackup: () => void;
  onOpenAdminPanel: () => void;
  onInstallPwa: () => Promise<void>;
  onLogout: () => Promise<void>;
  strings: AppStrings;
}

export function SettingsPage({
  userProfile,
  settings,
  isAdmin,
  canInstallPwa,
  isPwaInstalled,
  onChangeLanguage,
  onChangeTheme,
  onOpenSetupPin,
  onDisablePin,
  onToggleExpiryReminders,
  onCheckExpiriesNow,
  onOpenBackup,
  onOpenAdminPanel,
  onInstallPwa,
  onLogout,
  strings,
}: SettingsPageProps) {
  const [infoModal, setInfoModal] = useState<'privacy' | 'about' | null>(null);
  const [confirmLogout, setConfirmLogout] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <h1 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>{strings.settingsTitle}</h1>

      {userProfile && (
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #172554, #38BDF8)',
              color: '#FFFFFF',
              display: 'grid',
              placeItems: 'center',
              fontWeight: 800,
              fontSize: 18,
              flexShrink: 0,
            }}
          >
            {(userProfile.displayName || 'M')[0].toUpperCase()}
          </div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontWeight: 800, fontSize: 16 }}>{userProfile.displayName}</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>{userProfile.email}</div>
          </div>
          <span
            className="pill"
            style={{
              background: isAdmin ? '#172554' : 'var(--sky-soft)',
              color: isAdmin ? '#38BDF8' : '#172554',
            }}
          >
            {isAdmin ? strings.roleAdmin : strings.roleMember}
          </span>
        </div>
      )}

      {/* Language & Theme */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontWeight: 700, fontSize: 15 }}>{strings.languageSetting}</span>
          <LanguageSwitcherPill language={settings.languageCode} onChange={onChangeLanguage} />
        </div>

        <div
          style={{
            borderTop: '1px solid var(--border-color)',
            paddingTop: 14,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontWeight: 700, fontSize: 15 }}>{strings.themeSetting}</span>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className={`filter-chip ${settings.themeMode === 'light' ? 'selected' : ''}`}
              onClick={() => onChangeTheme('light')}
              data-testid="theme-light"
            >
              <Sun size={14} />
              <span>{strings.lightMode}</span>
            </button>
            <button
              type="button"
              className={`filter-chip ${settings.themeMode === 'dark' ? 'selected' : ''}`}
              onClick={() => onChangeTheme('dark')}
              data-testid="theme-dark"
            >
              <Moon size={14} />
              <span>{strings.darkMode}</span>
            </button>
            <button
              type="button"
              className={`filter-chip ${settings.themeMode === 'system' ? 'selected' : ''}`}
              onClick={() => onChangeTheme('system')}
              data-testid="theme-system"
            >
              <Monitor size={14} />
              <span>{strings.systemMode}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Security PIN & Expiry Reminders */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <KeyRound size={20} color="#38BDF8" />
            <div>
              <div style={{ fontWeight: 700, fontSize: 15 }}>{strings.appPinLock}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {settings.pinEnabled ? '4-digit PIN active' : 'Optional session PIN protection'}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" className="btn btn-outline" onClick={onOpenSetupPin}>
              {settings.pinEnabled ? strings.changePin : strings.appPinLock}
            </button>
            {settings.pinEnabled && (
              <button type="button" className="btn btn-danger" onClick={onDisablePin}>
                {strings.disablePin}
              </button>
            )}
          </div>
        </div>

        <div
          style={{
            borderTop: '1px solid var(--border-color)',
            paddingTop: 14,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
            <BellRing size={20} color="#F59E0B" />
            <div>
              <div style={{ fontWeight: 700, fontSize: 15 }}>{strings.expiryRemindersSetting}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {strings.expiryRemindersSubtitle}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button type="button" className="btn btn-outline" onClick={onCheckExpiriesNow}>
              {strings.checkExpiriesNow}
            </button>
            <input
              type="checkbox"
              checked={settings.expiryRemindersEnabled}
              onChange={(e) => onToggleExpiryReminders(e.target.checked)}
              aria-label={strings.expiryRemindersSetting}
              style={{ width: 20, height: 20, accentColor: '#172554', cursor: 'pointer' }}
            />
          </div>
        </div>
      </div>

      {/* Shortcuts & Info */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <button
          type="button"
          className="btn btn-outline"
          style={{ justifyContent: 'flex-start' }}
          onClick={onOpenBackup}
        >
          <Cloud size={18} color="#38BDF8" />
          <span>{strings.backupSettingsLink}</span>
        </button>

        {isAdmin && (
          <button
            type="button"
            className="btn btn-outline"
            style={{ justifyContent: 'flex-start' }}
            onClick={onOpenAdminPanel}
          >
            <ShieldCheck size={18} color="#172554" />
            <span>{strings.adminConsoleButton}</span>
          </button>
        )}

        {(canInstallPwa || isPwaInstalled) && (
          <button
            type="button"
            className="btn btn-outline"
            style={{ justifyContent: 'flex-start' }}
            disabled={isPwaInstalled}
            onClick={() => void onInstallPwa()}
          >
            {isPwaInstalled ? (
              <CheckCircle2 size={18} color="#10B981" />
            ) : (
              <Download size={18} color="#38BDF8" />
            )}
            <span>
              {isPwaInstalled ? strings.pwaInstalledBadge : strings.installPwaButton}
            </span>
          </button>
        )}

        <button
          type="button"
          className="btn btn-outline"
          style={{ justifyContent: 'flex-start' }}
          onClick={() => setInfoModal('privacy')}
        >
          <Shield size={18} color="#10B981" />
          <span>{strings.privacyPolicy}</span>
        </button>

        <button
          type="button"
          className="btn btn-outline"
          style={{ justifyContent: 'flex-start' }}
          onClick={() => setInfoModal('about')}
        >
          <Info size={18} color="#64748B" />
          <span>{strings.aboutApp}</span>
        </button>
      </div>

      <button
        type="button"
        className="btn btn-danger"
        style={{ width: '100%' }}
        onClick={() => setConfirmLogout(true)}
        data-testid="logout-btn"
      >
        <LogOut size={18} />
        <span>{strings.logout}</span>
      </button>

      {infoModal && (
        <div className="modal-backdrop" onClick={() => setInfoModal(null)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 10px' }}>
              {infoModal === 'privacy' ? strings.privacyPolicy : strings.aboutApp}
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 14, whiteSpace: 'pre-line', lineHeight: 1.6 }}>
              {infoModal === 'privacy' ? strings.privacyPolicyBody : strings.aboutAppBody}
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button type="button" className="btn btn-primary" onClick={() => setInfoModal(null)}>
                {strings.close}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmLogout && (
        <div className="modal-backdrop" onClick={() => setConfirmLogout(false)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px' }}>{strings.logout}</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>{strings.logoutConfirm}</p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
              <button type="button" className="btn btn-outline" onClick={() => setConfirmLogout(false)}>
                {strings.cancel}
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={async () => {
                  setConfirmLogout(false);
                  await onLogout();
                }}
              >
                {strings.logout}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
