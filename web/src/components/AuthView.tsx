import React, { useState } from 'react';
import {
  Auth,
  createUserWithEmailAndPassword,
  GoogleAuthProvider,
  signInWithEmailAndPassword,
  signInWithPopup,
  updateProfile,
} from 'firebase/auth';
import { Lock, Shield, ShieldCheck, WifiOff } from 'lucide-react';
import { LanguageCode } from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { LanguageSwitcherPill } from './SharedComponents';

interface AuthViewProps {
  auth: Auth | null;
  googleProvider: GoogleAuthProvider | null;
  firebaseConfigured: boolean;
  language: LanguageCode;
  onChangeLanguage: (lang: LanguageCode) => void;
  strings: AppStrings;
}

export function AuthView({
  auth,
  googleProvider,
  firebaseConfigured,
  language,
  onChangeLanguage,
  strings,
}: AuthViewProps) {
  const [mode, setMode] = useState<'signin' | 'register'>('signin');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleGoogleSignIn = async () => {
    if (!auth || !googleProvider) {
      setErrorMsg(strings.firebaseNotConfiguredBody);
      return;
    }
    setErrorMsg(null);
    setIsLoading(true);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Google sign-in failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!auth) {
      setErrorMsg(strings.firebaseNotConfiguredBody);
      return;
    }
    const cleanEmail = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      setErrorMsg(strings.invalidEmailError);
      return;
    }
    if (password.length < 6) {
      setErrorMsg(strings.passwordTooShortError);
      return;
    }
    if (mode === 'register' && fullName.trim().length < 2) {
      setErrorMsg(strings.nameRequiredError);
      return;
    }

    setErrorMsg(null);
    setIsLoading(true);
    try {
      if (mode === 'signin') {
        await signInWithEmailAndPassword(auth, cleanEmail, password);
      } else {
        const cred = await createUserWithEmailAndPassword(auth, cleanEmail, password);
        await updateProfile(cred.user, { displayName: fullName.trim() });
      }
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Authentication failed.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'linear-gradient(180deg, #0F172A 0%, #172554 45%, var(--bg-app) 100%)',
        padding: '20px 16px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 540,
          display: 'flex',
          justifyContent: 'flex-end',
          marginBottom: 16,
        }}
      >
        <LanguageSwitcherPill language={language} onChange={onChangeLanguage} />
      </div>

      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: 540,
          padding: 0,
          overflow: 'hidden',
          borderRadius: 26,
        }}
      >
        <div style={{ position: 'relative', height: 210 }}>
          <img
            src="/assets/img_vault_hero.jpg"
            alt={strings.appName}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: 'linear-gradient(180deg, transparent 10%, rgba(15, 23, 42, 0.88) 100%)',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-end',
              padding: 22,
              color: '#FFFFFF',
            }}
          >
            <span
              className="pill"
              style={{
                alignSelf: 'flex-start',
                background: 'rgba(56, 189, 248, 0.22)',
                border: '1px solid rgba(56, 189, 248, 0.6)',
                color: '#FFFFFF',
                marginBottom: 8,
              }}
            >
              <Shield size={14} color="#38BDF8" />
              <span>{strings.appName}</span>
            </span>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800 }}>{strings.tagline}</h1>
          </div>
        </div>

        <div style={{ padding: 24 }}>
          <h2 style={{ margin: '0 0 6px', fontSize: 20, fontWeight: 800, textAlign: 'center' }}>
            {strings.welcomeTitle}
          </h2>
          <p
            style={{
              margin: '0 0 18px',
              fontSize: 14,
              color: 'var(--text-muted)',
              textAlign: 'center',
              lineHeight: 1.5,
            }}
          >
            {strings.welcomeSubtitle}
          </p>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 8,
              marginBottom: 20,
            }}
          >
            <div
              style={{
                background: 'var(--sky-soft)',
                color: '#172554',
                borderRadius: 12,
                padding: '10px 6px',
                textAlign: 'center',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              <Lock size={16} style={{ marginBottom: 4 }} />
              <div>{strings.securityEncryptedBadge}</div>
            </div>
            <div
              style={{
                background: 'var(--sky-soft)',
                color: '#172554',
                borderRadius: 12,
                padding: '10px 6px',
                textAlign: 'center',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              <ShieldCheck size={16} style={{ marginBottom: 4 }} />
              <div>{strings.securityFamilyIsolatedBadge}</div>
            </div>
            <div
              style={{
                background: 'var(--sky-soft)',
                color: '#172554',
                borderRadius: 12,
                padding: '10px 6px',
                textAlign: 'center',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              <WifiOff size={16} style={{ marginBottom: 4 }} />
              <div>{strings.securityOfflinePwaBadge}</div>
            </div>
          </div>

          {!firebaseConfigured && (
            <div
              style={{
                background: '#FEF3C7',
                color: '#92400E',
                borderRadius: 14,
                padding: 14,
                fontSize: 13,
                marginBottom: 16,
                border: '1px solid rgba(245, 158, 11, 0.45)',
              }}
            >
              <strong>{strings.firebaseNotConfiguredTitle}</strong>
              <div style={{ marginTop: 4 }}>{strings.firebaseNotConfiguredBody}</div>
            </div>
          )}

          {errorMsg && (
            <div
              style={{
                background: '#FEE2E2',
                color: '#991B1B',
                borderRadius: 12,
                padding: 12,
                fontSize: 13,
                marginBottom: 14,
              }}
            >
              {errorMsg}
            </div>
          )}

          <button
            type="button"
            className="btn btn-primary"
            style={{ width: '100%' }}
            onClick={handleGoogleSignIn}
            disabled={isLoading || !firebaseConfigured}
            data-testid="google-signin-button"
          >
            {isLoading ? strings.signingIn : strings.signInWithGoogle}
          </button>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              margin: '16px 0',
              color: 'var(--text-muted)',
              fontSize: 12,
              fontWeight: 700,
            }}
          >
            <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
            <span>{strings.orDivider}</span>
            <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: 6,
              padding: 4,
              borderRadius: 12,
              background: 'var(--bg-surface-variant)',
              marginBottom: 14,
            }}
          >
            <button
              type="button"
              onClick={() => {
                setMode('signin');
                setErrorMsg(null);
              }}
              style={{
                border: 'none',
                borderRadius: 10,
                minHeight: 38,
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
                background: mode === 'signin' ? 'var(--bg-surface)' : 'transparent',
                color: 'var(--text-primary)',
              }}
            >
              {strings.signInWithEmailTab}
            </button>
            <button
              type="button"
              onClick={() => {
                setMode('register');
                setErrorMsg(null);
              }}
              style={{
                border: 'none',
                borderRadius: 10,
                minHeight: 38,
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
                background: mode === 'register' ? 'var(--bg-surface)' : 'transparent',
                color: 'var(--text-primary)',
              }}
            >
              {strings.registerWithEmailTab}
            </button>
          </div>

          <form onSubmit={handleEmailSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {mode === 'register' && (
              <div>
                <label className="label">{strings.yourNameLabel}</label>
                <input
                  className="input"
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Aarav Sharma"
                  required
                />
              </div>
            )}
            <div>
              <label className="label">{strings.emailLabel}</label>
              <input
                className="input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                required
              />
            </div>
            <div>
              <label className="label">{strings.passwordLabel}</label>
              <input
                className="input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={6}
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-sky"
              style={{ width: '100%' }}
              disabled={isLoading || !firebaseConfigured}
              data-testid="email-auth-submit"
            >
              {isLoading
                ? strings.signingIn
                : mode === 'signin'
                  ? strings.signInButton
                  : strings.registerButton}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
