import React, { useState } from 'react';
import { Delete, Lock } from 'lucide-react';
import { AppStrings } from '../localization/appStrings';

interface PinLockModalProps {
  mode: 'unlock' | 'setup';
  verifyPin: (pin: string) => Promise<boolean>;
  onSuccess: (pin: string) => void;
  onCancel?: () => void;
  strings: AppStrings;
}

export function PinLockModal({
  mode,
  verifyPin,
  onSuccess,
  onCancel,
  strings,
}: PinLockModalProps) {
  const [entered, setEntered] = useState('');
  const [firstPin, setFirstPin] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleDigit = async (d: string) => {
    if (entered.length >= 4) return;
    setError(null);
    const next = entered + d;
    setEntered(next);
    if (next.length === 4) {
      if (mode === 'unlock') {
        const ok = await verifyPin(next);
        if (ok) {
          onSuccess(next);
        } else {
          setError(strings.wrongPinError);
          setEntered('');
        }
      } else if (firstPin === null) {
        setFirstPin(next);
        setEntered('');
      } else if (firstPin === next) {
        onSuccess(next);
      } else {
        setError(strings.pinMismatchError);
        setFirstPin(null);
        setEntered('');
      }
    }
  };

  const title =
    mode === 'unlock'
      ? strings.enterPinTitle
      : firstPin === null
        ? strings.setupPinTitle
        : strings.confirmPinTitle;

  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'DEL'];

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 75,
        background: 'var(--bg-app)',
        display: 'grid',
        placeItems: 'center',
        padding: 20,
      }}
    >
      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: 380,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          padding: 28,
        }}
      >
        <div
          style={{
            width: 60,
            height: 60,
            borderRadius: '50%',
            background: '#172554',
            color: '#38BDF8',
            display: 'grid',
            placeItems: 'center',
            marginBottom: 14,
          }}
        >
          <Lock size={28} />
        </div>
        <h2 style={{ margin: '0 0 6px', fontSize: 20, textAlign: 'center' }}>{title}</h2>
        <p style={{ margin: '0 0 22px', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center' }}>
          {strings.pinSubtitle}
        </p>

        <div style={{ display: 'flex', gap: 14, marginBottom: 16 }}>
          {[0, 1, 2, 3].map((idx) => (
            <div
              key={idx}
              style={{
                width: 16,
                height: 16,
                borderRadius: '50%',
                background: idx < entered.length ? '#172554' : 'transparent',
                border: `2px solid ${idx < entered.length ? '#38BDF8' : 'var(--border-color)'}`,
              }}
            />
          ))}
        </div>

        {error && (
          <div style={{ color: '#EF4444', fontSize: 13, marginBottom: 12, textAlign: 'center' }}>
            {error}
          </div>
        )}

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 68px)',
            gap: 14,
            marginTop: 6,
          }}
        >
          {keys.map((k, idx) =>
            k === '' ? (
              <div key={idx} />
            ) : (
              <button
                key={idx}
                type="button"
                onClick={() =>
                  k === 'DEL' ? setEntered((prev) => prev.slice(0, -1)) : void handleDigit(k)
                }
                style={{
                  width: 68,
                  height: 68,
                  borderRadius: '50%',
                  border: '1px solid var(--border-color)',
                  background: 'var(--bg-surface)',
                  color: 'var(--text-primary)',
                  fontSize: 20,
                  fontWeight: 700,
                  display: 'grid',
                  placeItems: 'center',
                  cursor: 'pointer',
                }}
              >
                {k === 'DEL' ? <Delete size={20} /> : k}
              </button>
            )
          )}
        </div>

        {onCancel && (
          <button
            type="button"
            className="btn btn-outline"
            style={{ marginTop: 18, width: '100%' }}
            onClick={onCancel}
          >
            {strings.cancel}
          </button>
        )}
      </div>
    </div>
  );
}
