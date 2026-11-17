import React, { useState } from 'react';
import { LogOut, ShieldPlus, Users } from 'lucide-react';
import { LanguageCode } from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { LanguageSwitcherPill } from './SharedComponents';

interface OnboardingViewProps {
  defaultName: string;
  language: LanguageCode;
  isBusy: boolean;
  errorMessage: string | null;
  onChangeLanguage: (lang: LanguageCode) => void;
  onCreateFamily: (familyName: string, creatorName: string) => Promise<void>;
  onJoinFamily: (familyCode: string, memberName: string) => Promise<void>;
  onSignOut: () => void;
  strings: AppStrings;
}

export function OnboardingView({
  defaultName,
  language,
  isBusy,
  errorMessage,
  onChangeLanguage,
  onCreateFamily,
  onJoinFamily,
  onSignOut,
  strings,
}: OnboardingViewProps) {
  const [tab, setTab] = useState<'create' | 'join'>('create');
  const [familyName, setFamilyName] = useState('');
  const [familyCode, setFamilyCode] = useState('');
  const [yourName, setYourName] = useState(defaultName);

  const canSubmit =
    tab === 'create' ? familyName.trim().length >= 2 : familyCode.trim().length >= 6;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit || isBusy) return;
    if (tab === 'create') {
      await onCreateFamily(familyName, yourName);
    } else {
      await onJoinFamily(familyCode, yourName);
    }
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--bg-app)',
        padding: '20px 16px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 560,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 14,
        }}
      >
        <LanguageSwitcherPill language={language} onChange={onChangeLanguage} />
        <button type="button" className="btn btn-outline" onClick={onSignOut}>
          <LogOut size={16} />
          <span>{strings.logout}</span>
        </button>
      </div>

      <div className="card" style={{ width: '100%', maxWidth: 560, padding: 22 }}>
        <img
          src="/assets/img_family_shield.jpg"
          alt={strings.appName}
          style={{
            width: '100%',
            height: 165,
            objectFit: 'cover',
            borderRadius: 16,
            marginBottom: 16,
          }}
        />
        <h2 style={{ margin: '0 0 6px', textAlign: 'center', fontSize: 22, fontWeight: 800 }}>
          {strings.appName}
        </h2>
        <p
          style={{
            margin: '0 0 18px',
            textAlign: 'center',
            color: 'var(--text-muted)',
            fontSize: 14,
          }}
        >
          {strings.onboardingSubtitle}
        </p>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 6,
            padding: 4,
            borderRadius: 12,
            background: 'var(--bg-surface-variant)',
            marginBottom: 16,
          }}
        >
          <button
            type="button"
            onClick={() => setTab('create')}
            style={{
              border: 'none',
              borderRadius: 10,
              minHeight: 40,
              fontWeight: 700,
              cursor: 'pointer',
              background: tab === 'create' ? 'var(--navy-primary)' : 'transparent',
              color: tab === 'create' ? '#FFFFFF' : 'var(--text-primary)',
            }}
          >
            {strings.createFamilyTab}
          </button>
          <button
            type="button"
            onClick={() => setTab('join')}
            style={{
              border: 'none',
              borderRadius: 10,
              minHeight: 40,
              fontWeight: 700,
              cursor: 'pointer',
              background: tab === 'join' ? 'var(--navy-primary)' : 'transparent',
              color: tab === 'join' ? '#FFFFFF' : 'var(--text-primary)',
            }}
          >
            {strings.joinFamilyTab}
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {tab === 'create' ? (
            <div>
              <label className="label">{strings.familyNameLabel}</label>
              <input
                className="input"
                type="text"
                value={familyName}
                onChange={(e) => setFamilyName(e.target.value)}
                minLength={2}
                required
              />
            </div>
          ) : (
            <div>
              <label className="label">{strings.familyIdCodeLabel}</label>
              <input
                className="input"
                type="text"
                value={familyCode}
                onChange={(e) => setFamilyCode(e.target.value.toUpperCase())}
                minLength={6}
                required
              />
            </div>
          )}

          <div>
            <label className="label">{strings.yourNameLabel}</label>
            <input
              className="input"
              type="text"
              value={yourName}
              onChange={(e) => setYourName(e.target.value)}
              required
            />
          </div>

          {errorMessage && (
            <div
              style={{
                background: '#FEE2E2',
                color: '#991B1B',
                borderRadius: 12,
                padding: 12,
                fontSize: 13,
              }}
            >
              {errorMessage}
            </div>
          )}

          <button
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', marginTop: 6 }}
            disabled={!canSubmit || isBusy}
          >
            {tab === 'create' ? <ShieldPlus size={18} /> : <Users size={18} />}
            <span>{tab === 'create' ? strings.createFamilyButton : strings.joinFamilyButton}</span>
          </button>
        </form>
      </div>
    </div>
  );
}
