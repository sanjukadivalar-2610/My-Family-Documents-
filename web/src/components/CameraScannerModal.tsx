import React, { useEffect, useRef, useState } from 'react';
import { Camera, RotateCcw, Upload, X } from 'lucide-react';
import { AppStrings } from '../localization/appStrings';

interface CameraScannerModalProps {
  onClose: () => void;
  onCaptured: (file: File) => void;
  strings: AppStrings;
}

export function CameraScannerModal({
  onClose,
  onCaptured,
  strings,
}: CameraScannerModalProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [capturedUrl, setCapturedUrl] = useState<string | null>(null);
  const [capturedFile, setCapturedFile] = useState<File | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function startCamera() {
      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraError(strings.cameraPermissionHint);
        return;
      }
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        });
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      } catch (err) {
        setCameraError(err instanceof Error ? err.message : strings.cameraPermissionHint);
      }
    }
    void startCamera();
    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, [strings.cameraPermissionHint]);

  const handleCapture = () => {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        const file = new File([blob], `scan_${Date.now()}.jpg`, { type: 'image/jpeg' });
        setCapturedFile(file);
        setCapturedUrl(URL.createObjectURL(blob));
      },
      'image/jpeg',
      0.92
    );
  };

  const handleFallbackPhoto = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setCapturedFile(file);
    setCapturedUrl(URL.createObjectURL(file));
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel modal-panel-wide" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 14,
          }}
        >
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>{strings.scanWithCamera}</h3>
          <button type="button" className="btn btn-outline" style={{ minHeight: 38, padding: 8 }} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div
          style={{
            background: '#0F172A',
            borderRadius: 16,
            overflow: 'hidden',
            position: 'relative',
            minHeight: 320,
            display: 'grid',
            placeItems: 'center',
          }}
        >
          {capturedUrl ? (
            <img
              src={capturedUrl}
              alt="Scanned document preview"
              style={{ width: '100%', maxHeight: 460, objectFit: 'contain' }}
            />
          ) : cameraError ? (
            <div style={{ padding: 24, textAlign: 'center', color: '#E0F2FE', maxWidth: 420 }}>
              <Camera size={40} color="#38BDF8" style={{ marginBottom: 10 }} />
              <p style={{ margin: '0 0 14px', fontSize: 14 }}>{cameraError}</p>
              <label className="btn btn-sky" style={{ cursor: 'pointer' }}>
                <Upload size={16} />
                <span>{strings.uploadPhotoFallback}</span>
                <input
                  type="file"
                  accept="image/*"
                  capture="environment"
                  onChange={handleFallbackPhoto}
                  style={{ display: 'none' }}
                />
              </label>
            </div>
          ) : (
            <>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                style={{ width: '100%', maxHeight: 460, objectFit: 'cover' }}
              />
              <div
                style={{
                  position: 'absolute',
                  inset: '12% 8%',
                  border: '2px dashed #38BDF8',
                  borderRadius: 16,
                  pointerEvents: 'none',
                }}
              />
            </>
          )}
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 16, flexWrap: 'wrap' }}>
          {capturedFile ? (
            <>
              <button
                type="button"
                className="btn btn-outline"
                onClick={() => {
                  if (capturedUrl) URL.revokeObjectURL(capturedUrl);
                  setCapturedUrl(null);
                  setCapturedFile(null);
                }}
              >
                <RotateCcw size={16} />
                <span>{strings.retakeScan}</span>
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => onCaptured(capturedFile)}
              >
                <span>{strings.useScan}</span>
              </button>
            </>
          ) : (
            <>
              <label className="btn btn-outline" style={{ cursor: 'pointer' }}>
                <Upload size={16} />
                <span>{strings.uploadPhotoFallback}</span>
                <input
                  type="file"
                  accept="image/*"
                  capture="environment"
                  onChange={handleFallbackPhoto}
                  style={{ display: 'none' }}
                />
              </label>
              {!cameraError && (
                <button type="button" className="btn btn-sky" onClick={handleCapture}>
                  <Camera size={16} />
                  <span>{strings.captureScan}</span>
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
