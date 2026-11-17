import React, { useEffect, useMemo, useState } from 'react';
import {
  Camera,
  Download,
  Edit2,
  FileText,
  Lock,
  Plus,
  Search,
  Share2,
  Sparkles,
  Trash2,
  Upload,
  X,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import {
  DOCUMENT_CATEGORIES,
  getCategoryByKey,
  getLocalizedCategoryLabel,
} from '../domain/documentCategory';
import {
  FamilyAccount,
  FamilyDocument,
  FamilyMember,
  formatDateMillis,
  formatFileSize,
} from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { DocumentCard, ExpiryBadge } from '../components/SharedComponents';
import { CameraScannerModal } from '../components/CameraScannerModal';

interface DocumentsPageProps {
  family: FamilyAccount;
  documents: FamilyDocument[];
  members: FamilyMember[];
  isAdmin: boolean;
  initialCategoryFilter: string | null;
  initialMemberFilter: string | null;
  openUploadInitially: boolean;
  openScannerInitially: boolean;
  selectedDocumentId: string | null;
  onConsumeInitialFilters: () => void;
  onSelectDocumentId: (id: string | null) => void;
  onUploadDocument: (params: {
    file: File;
    title: string;
    categoryKey: string;
    member: FamilyMember | null;
    maskedId: string;
    notes: string;
    issueDateMillis: number;
    expiryDateMillis: number;
  }) => Promise<void>;
  onLoadSamples: () => Promise<void>;
  onLoadDecryptedBytes: (doc: FamilyDocument) => Promise<Uint8Array | null>;
  onUpdateDocument: (params: {
    doc: FamilyDocument;
    newTitle: string;
    categoryKey: string;
    issueDateMillis: number;
    expiryDateMillis: number;
    notes: string;
  }) => Promise<void>;
  onUpdatePermissions: (doc: FamilyDocument, allowedUids: string[]) => Promise<void>;
  onDeleteDocument: (doc: FamilyDocument) => Promise<void>;
  onShowToast: (msg: string) => void;
  strings: AppStrings;
}

function toDateInputValue(millis: number): string {
  if (!millis || millis <= 0) return '';
  return new Date(millis).toISOString().slice(0, 10);
}

function fromDateInputValue(val: string): number {
  if (!val) return 0;
  const parsed = Date.parse(val);
  return Number.isNaN(parsed) ? 0 : parsed;
}

export function DocumentsPage({
  family,
  documents,
  members,
  isAdmin,
  initialCategoryFilter,
  initialMemberFilter,
  openUploadInitially,
  openScannerInitially,
  selectedDocumentId,
  onConsumeInitialFilters,
  onSelectDocumentId,
  onUploadDocument,
  onLoadSamples,
  onLoadDecryptedBytes,
  onUpdateDocument,
  onUpdatePermissions,
  onDeleteDocument,
  onShowToast,
  strings,
}: DocumentsPageProps) {
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string | null>(initialCategoryFilter);
  const [memberFilter, setMemberFilter] = useState<string | null>(initialMemberFilter);
  const [showUploadModal, setShowUploadModal] = useState(openUploadInitially);
  const [showScanner, setShowScanner] = useState(openScannerInitially);
  const [preselectedScanFile, setPreselectedScanFile] = useState<File | null>(null);

  useEffect(() => {
    if (initialCategoryFilter) setCategoryFilter(initialCategoryFilter);
    if (initialMemberFilter) setMemberFilter(initialMemberFilter);
    if (openUploadInitially) setShowUploadModal(true);
    if (openScannerInitially) setShowScanner(true);
    if (
      initialCategoryFilter ||
      initialMemberFilter ||
      openUploadInitially ||
      openScannerInitially
    ) {
      onConsumeInitialFilters();
    }
  }, [
    initialCategoryFilter,
    initialMemberFilter,
    openUploadInitially,
    openScannerInitially,
    onConsumeInitialFilters,
  ]);

  const filteredDocs = useMemo(() => {
    const q = search.trim().toLowerCase();
    return documents.filter((doc) => {
      const matchesQ =
        !q ||
        doc.title.toLowerCase().includes(q) ||
        doc.category.toLowerCase().includes(q) ||
        doc.memberName.toLowerCase().includes(q) ||
        (doc.notes?.toLowerCase().includes(q) ?? false) ||
        (doc.documentNumberMasked?.toLowerCase().includes(q) ?? false);
      const matchesCat = !categoryFilter || doc.category === categoryFilter;
      const matchesMember = !memberFilter || doc.memberId === memberFilter;
      return matchesQ && matchesCat && matchesMember;
    });
  }, [documents, search, categoryFilter, memberFilter]);

  const selectedDoc = useMemo(
    () => (selectedDocumentId ? documents.find((d) => d.id === selectedDocumentId) || null : null),
    [documents, selectedDocumentId]
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <h1 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>{strings.documentsTitle}</h1>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" className="btn btn-outline" onClick={() => setShowScanner(true)}>
            <Camera size={16} />
            <span>{strings.scanDocument}</span>
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setPreselectedScanFile(null);
              setShowUploadModal(true);
            }}
            data-testid="open-upload-modal"
          >
            <Plus size={16} />
            <span>{strings.quickUpload}</span>
          </button>
        </div>
      </div>

      {/* Search Bar */}
      <div style={{ position: 'relative' }}>
        <Search
          size={18}
          color="var(--text-muted)"
          style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)' }}
        />
        <input
          className="input"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={strings.searchDocumentsHint}
          style={{ paddingLeft: 42 }}
          data-testid="documents-search"
        />
      </div>

      {/* 12 Categories Filter */}
      <div className="chip-row">
        <button
          type="button"
          className={`filter-chip ${categoryFilter === null ? 'selected' : ''}`}
          onClick={() => setCategoryFilter(null)}
        >
          {strings.allCategories}
        </button>
        {DOCUMENT_CATEGORIES.map((cat) => (
          <button
            key={cat.id}
            type="button"
            className={`filter-chip ${categoryFilter === cat.storageKey ? 'selected' : ''}`}
            onClick={() =>
              setCategoryFilter((prev) => (prev === cat.storageKey ? null : cat.storageKey))
            }
          >
            {getLocalizedCategoryLabel(cat, strings.languageCode)}
          </button>
        ))}
      </div>

      {/* Member Filter */}
      {members.length > 0 && (
        <div className="chip-row">
          <button
            type="button"
            className={`filter-chip ${memberFilter === null ? 'selected' : ''}`}
            onClick={() => setMemberFilter(null)}
          >
            {strings.allMembers}
          </button>
          {members.map((m) => (
            <button
              key={m.id}
              type="button"
              className={`filter-chip ${memberFilter === m.id ? 'selected' : ''}`}
              onClick={() => setMemberFilter((prev) => (prev === m.id ? null : m.id))}
            >
              {m.name}
            </button>
          ))}
        </div>
      )}

      {/* Documents List */}
      {filteredDocs.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 32 }}>
          <FileText size={40} color="#38BDF8" style={{ marginBottom: 10 }} />
          <div style={{ fontWeight: 700, fontSize: 17 }}>{strings.emptyDocumentsTitle}</div>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, maxWidth: 460, margin: '8px auto 18px' }}>
            {strings.emptyDocumentsSubtitle}
          </p>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setPreselectedScanFile(null);
                setShowUploadModal(true);
              }}
            >
              <Upload size={16} />
              <span>{strings.quickUpload}</span>
            </button>
            <button type="button" className="btn btn-outline" onClick={() => void onLoadSamples()}>
              <Sparkles size={16} color="#10B981" />
              <span>{strings.loadSampleDocs}</span>
            </button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {filteredDocs.map((doc) => (
            <DocumentCard
              key={doc.id}
              document={doc}
              strings={strings}
              onClick={() => onSelectDocumentId(doc.id)}
            />
          ))}
        </div>
      )}

      {showScanner && (
        <CameraScannerModal
          onClose={() => setShowScanner(false)}
          onCaptured={(file) => {
            setShowScanner(false);
            setPreselectedScanFile(file);
            setShowUploadModal(true);
          }}
          strings={strings}
        />
      )}

      {showUploadModal && (
        <UploadDocumentModal
          members={members}
          initialCategory={categoryFilter || DOCUMENT_CATEGORIES[0].storageKey}
          initialFile={preselectedScanFile}
          onClose={() => {
            setShowUploadModal(false);
            setPreselectedScanFile(null);
          }}
          onUpload={onUploadDocument}
          strings={strings}
        />
      )}

      {selectedDoc && (
        <DocumentDetailModal
          family={family}
          document={selectedDoc}
          members={members}
          isAdmin={isAdmin}
          onClose={() => onSelectDocumentId(null)}
          onLoadDecryptedBytes={onLoadDecryptedBytes}
          onUpdateDocument={onUpdateDocument}
          onUpdatePermissions={onUpdatePermissions}
          onDeleteDocument={onDeleteDocument}
          onShowToast={onShowToast}
          strings={strings}
        />
      )}
    </div>
  );
}

function UploadDocumentModal({
  members,
  initialCategory,
  initialFile,
  onClose,
  onUpload,
  strings,
}: {
  members: FamilyMember[];
  initialCategory: string;
  initialFile: File | null;
  onClose: () => void;
  onUpload: DocumentsPageProps['onUploadDocument'];
  strings: AppStrings;
}) {
  const [file, setFile] = useState<File | null>(initialFile);
  const [title, setTitle] = useState('');
  const [categoryKey, setCategoryKey] = useState(initialCategory);
  const [memberId, setMemberId] = useState(members[0]?.id || '');
  const [maskedId, setMaskedId] = useState('');
  const [notes, setNotes] = useState('');
  const [issueDate, setIssueDate] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (!selected) return;
    const ext = selected.name.split('.').pop()?.toLowerCase() || '';
    if (!['pdf', 'jpg', 'jpeg', 'png'].includes(ext)) {
      setErrorMsg(strings.unsupportedFileError);
      return;
    }
    setErrorMsg(null);
    setFile(selected);
    if (!title.trim()) {
      setTitle(selected.name.replace(/\.[^/.]+$/, ''));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setErrorMsg(strings.fileRequiredError);
      return;
    }
    setErrorMsg(null);
    setIsBusy(true);
    try {
      const selectedMember = members.find((m) => m.id === memberId) || members[0] || null;
      await onUpload({
        file,
        title: title.trim() || getLocalizedCategoryLabel(getCategoryByKey(categoryKey), strings.languageCode),
        categoryKey,
        member: selectedMember,
        maskedId: maskedId.trim(),
        notes: notes.trim(),
        issueDateMillis: fromDateInputValue(issueDate),
        expiryDateMillis: fromDateInputValue(expiryDate),
      });
      onClose();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Upload failed.');
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>{strings.uploadDocumentTitle}</h3>
          <button type="button" className="btn btn-outline" style={{ minHeight: 36, padding: 6 }} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <label className="label">{strings.uploadFileLabel}</label>
            <input
              className="input"
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              onChange={handleFileChange}
              style={{ padding: 9 }}
            />
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
              {file ? `${file.name} (${formatFileSize(file.size)})` : strings.supportedFormatsHint}
            </div>
          </div>

          <div>
            <label className="label">{strings.documentTitleLabel}</label>
            <input
              className="input"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={getLocalizedCategoryLabel(getCategoryByKey(categoryKey), strings.languageCode)}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label className="label">{strings.categoryLabel}</label>
              <select
                className="select"
                value={categoryKey}
                onChange={(e) => setCategoryKey(e.target.value)}
              >
                {DOCUMENT_CATEGORIES.map((cat) => (
                  <option key={cat.id} value={cat.storageKey}>
                    {getLocalizedCategoryLabel(cat, strings.languageCode)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="label">{strings.memberOwnerLabel}</label>
              <select
                className="select"
                value={memberId}
                onChange={(e) => setMemberId(e.target.value)}
              >
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.relation})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label className="label">{strings.issueDateLabel}</label>
              <input
                className="input"
                type="date"
                value={issueDate}
                onChange={(e) => setIssueDate(e.target.value)}
              />
            </div>
            <div>
              <label className="label">{strings.expiryDateLabel}</label>
              <input
                className="input"
                type="date"
                value={expiryDate}
                onChange={(e) => setExpiryDate(e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="label">{strings.docNumberOptionalLabel}</label>
            <input
              className="input"
              type="text"
              value={maskedId}
              onChange={(e) => setMaskedId(e.target.value)}
            />
          </div>

          <div>
            <label className="label">{strings.notesOptionalLabel}</label>
            <textarea
              className="textarea"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              style={{ minHeight: 68 }}
            />
          </div>

          {errorMsg && (
            <div style={{ background: '#FEE2E2', color: '#991B1B', padding: 10, borderRadius: 10, fontSize: 13 }}>
              {errorMsg}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 6 }}>
            <button type="button" className="btn btn-outline" onClick={onClose}>
              {strings.cancel}
            </button>
            <button type="submit" className="btn btn-primary" disabled={isBusy}>
              <Lock size={16} />
              <span>{strings.save}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function DocumentDetailModal({
  family,
  document,
  members,
  isAdmin,
  onClose,
  onLoadDecryptedBytes,
  onUpdateDocument,
  onUpdatePermissions,
  onDeleteDocument,
  onShowToast,
  strings,
}: {
  family: FamilyAccount;
  document: FamilyDocument;
  members: FamilyMember[];
  isAdmin: boolean;
  onClose: () => void;
  onLoadDecryptedBytes: (doc: FamilyDocument) => Promise<Uint8Array | null>;
  onUpdateDocument: DocumentsPageProps['onUpdateDocument'];
  onUpdatePermissions: (doc: FamilyDocument, allowedUids: string[]) => Promise<void>;
  onDeleteDocument: (doc: FamilyDocument) => Promise<void>;
  onShowToast: (msg: string) => void;
  strings: AppStrings;
}) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [decryptedBytes, setDecryptedBytes] = useState<Uint8Array | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [zoom, setZoom] = useState(1);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const [editTitle, setEditTitle] = useState(document.title);
  const [editCategory, setEditCategory] = useState(document.category);
  const [editIssue, setEditIssue] = useState(toDateInputValue(document.issueDateMillis));
  const [editExpiry, setEditExpiry] = useState(toDateInputValue(document.expiryDateMillis));
  const [editNotes, setEditNotes] = useState(document.notes || '');

  const mimeType =
    document.fileType === 'pdf'
      ? 'application/pdf'
      : document.fileType === 'png'
        ? 'image/png'
        : 'image/jpeg';

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    setIsLoading(true);
    void onLoadDecryptedBytes(document)
      .then((bytes) => {
        if (!active) return;
        setDecryptedBytes(bytes);
        if (bytes) {
          const blob = new Blob([bytes as BlobPart], { type: mimeType });
          objectUrl = URL.createObjectURL(blob);
          setPreviewUrl(objectUrl);
        } else {
          setPreviewUrl(null);
        }
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [document.id, document.storagePath, mimeType, onLoadDecryptedBytes]);

  const handleDownload = () => {
    if (!decryptedBytes) return;
    const blob = new Blob([decryptedBytes as BlobPart], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = window.document.createElement('a');
    link.href = url;
    link.download = document.fileName || `${document.title}.${document.fileType}`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    onShowToast(strings.downloadSuccess);
  };

  const handleShare = async () => {
    if (!decryptedBytes) return;
    const file = new File([decryptedBytes as BlobPart], document.fileName || 'document', {
      type: mimeType,
    });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      try {
        await navigator.share({ title: document.title, files: [file] });
        onShowToast(strings.shareSuccess);
        return;
      } catch {
        // User cancelled or browser refused -> fallback to download
      }
    }
    handleDownload();
  };

  const linkedMembers = members.filter((m) => Boolean(m.linkedUserId));
  const cat = getCategoryByKey(document.category);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel modal-panel-wide" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 10,
            marginBottom: 14,
          }}
        >
          <div style={{ minWidth: 0 }}>
            <h3 style={{ margin: 0, fontSize: 19, fontWeight: 800 }}>{document.title}</h3>
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
              {getLocalizedCategoryLabel(cat, strings.languageCode)} • {document.memberName}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            <button
              type="button"
              className="btn btn-outline"
              style={{ minHeight: 38, padding: '8px 10px' }}
              onClick={() => setEditing((v) => !v)}
              aria-label={strings.renameDocument}
            >
              <Edit2 size={16} />
            </button>
            <button
              type="button"
              className="btn btn-danger"
              style={{ minHeight: 38, padding: '8px 10px' }}
              onClick={() => setConfirmDelete(true)}
              aria-label={strings.deleteDocument}
            >
              <Trash2 size={16} />
            </button>
            <button
              type="button"
              className="btn btn-outline"
              style={{ minHeight: 38, padding: '8px 10px' }}
              onClick={onClose}
              aria-label={strings.close}
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Decrypted Preview */}
        <div className="card" style={{ marginBottom: 14, padding: 12 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 10,
              flexWrap: 'wrap',
              gap: 8,
            }}
          >
            <span className="pill" style={{ background: 'var(--sky-soft)', color: '#172554' }}>
              <Lock size={13} />
              <span>
                {strings.documentPreviewTitle} ({document.fileType.toUpperCase()})
              </span>
            </span>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <ExpiryBadge document={document} strings={strings} />
              {document.fileType !== 'pdf' && previewUrl && (
                <>
                  <button
                    type="button"
                    className="btn btn-outline"
                    style={{ minHeight: 32, padding: '4px 8px' }}
                    onClick={() => setZoom((z) => Math.max(1, z - 0.25))}
                    aria-label={strings.zoomOut}
                  >
                    <ZoomOut size={15} />
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline"
                    style={{ minHeight: 32, padding: '4px 8px' }}
                    onClick={() => setZoom((z) => Math.min(3, z + 0.25))}
                    aria-label={strings.zoomIn}
                  >
                    <ZoomIn size={15} />
                  </button>
                </>
              )}
            </div>
          </div>

          {isLoading ? (
            <div style={{ height: 260, display: 'grid', placeItems: 'center' }}>
              <div className="spinner" />
            </div>
          ) : !previewUrl ? (
            <div
              style={{
                height: 200,
                display: 'grid',
                placeItems: 'center',
                background: 'var(--bg-surface-variant)',
                borderRadius: 12,
                color: 'var(--text-muted)',
              }}
            >
              {strings.localOnlyBadge}
            </div>
          ) : document.fileType === 'pdf' ? (
            <iframe
              src={previewUrl}
              title={document.title}
              style={{
                width: '100%',
                height: 440,
                border: '1px solid var(--border-color)',
                borderRadius: 12,
                background: '#FFFFFF',
              }}
            />
          ) : (
            <div
              style={{
                overflow: 'auto',
                maxHeight: 440,
                borderRadius: 12,
                background: '#FFFFFF',
                textAlign: 'center',
              }}
            >
              <img
                src={previewUrl}
                alt={document.title}
                style={{
                  maxWidth: '100%',
                  transform: `scale(${zoom})`,
                  transformOrigin: 'top center',
                  transition: 'transform 0.15s ease',
                }}
              />
            </div>
          )}
        </div>

        {/* Download & Share */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 14, flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn btn-primary"
            style={{ flex: 1 }}
            disabled={!decryptedBytes}
            onClick={handleDownload}
          >
            <Download size={16} />
            <span>{strings.downloadDocument}</span>
          </button>
          <button
            type="button"
            className="btn btn-outline"
            style={{ flex: 1 }}
            disabled={!decryptedBytes}
            onClick={() => void handleShare()}
          >
            <Share2 size={16} />
            <span>{strings.shareDocument}</span>
          </button>
        </div>

        {/* Edit Metadata Form */}
        {editing && (
          <form
            className="card"
            style={{ marginBottom: 14, display: 'flex', flexDirection: 'column', gap: 10 }}
            onSubmit={async (e) => {
              e.preventDefault();
              await onUpdateDocument({
                doc: document,
                newTitle: editTitle,
                categoryKey: editCategory,
                issueDateMillis: fromDateInputValue(editIssue),
                expiryDateMillis: fromDateInputValue(editExpiry),
                notes: editNotes,
              });
              setEditing(false);
            }}
          >
            <div style={{ fontWeight: 700 }}>{strings.renameDocument}</div>
            <input
              className="input"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              required
            />
            <select
              className="select"
              value={editCategory}
              onChange={(e) => setEditCategory(e.target.value)}
            >
              {DOCUMENT_CATEGORIES.map((c) => (
                <option key={c.id} value={c.storageKey}>
                  {getLocalizedCategoryLabel(c, strings.languageCode)}
                </option>
              ))}
            </select>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label className="label">{strings.issueDateLabel}</label>
                <input
                  className="input"
                  type="date"
                  value={editIssue}
                  onChange={(e) => setEditIssue(e.target.value)}
                />
              </div>
              <div>
                <label className="label">{strings.expiryDateLabel}</label>
                <input
                  className="input"
                  type="date"
                  value={editExpiry}
                  onChange={(e) => setEditExpiry(e.target.value)}
                />
              </div>
            </div>
            <textarea
              className="textarea"
              rows={2}
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              placeholder={strings.notesOptionalLabel}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <button type="button" className="btn btn-outline" onClick={() => setEditing(false)}>
                {strings.cancel}
              </button>
              <button type="submit" className="btn btn-primary">
                {strings.save}
              </button>
            </div>
          </form>
        )}

        {/* Metadata Summary */}
        <div className="card" style={{ marginBottom: 14, display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
          <div>
            <strong>{strings.categoryLabel}:</strong> {getLocalizedCategoryLabel(cat, strings.languageCode)}
          </div>
          <div>
            <strong>{strings.memberOwnerLabel}:</strong> {document.memberName}
          </div>
          <div>
            <strong>File:</strong> {document.fileName} ({formatFileSize(document.fileSizeBytes)})
          </div>
          {document.documentNumberMasked && (
            <div>
              <strong>{strings.docNumberOptionalLabel}:</strong> {document.documentNumberMasked}
            </div>
          )}
          <div>
            <strong>{strings.issueDateLabel}:</strong> {formatDateMillis(document.issueDateMillis)}
          </div>
          <div>
            <strong>{strings.expiryDateLabel}:</strong>{' '}
            {document.expiryDateMillis > 0
              ? formatDateMillis(document.expiryDateMillis)
              : strings.noExpirySet}
          </div>
          {document.notes && (
            <div>
              <strong>{strings.notesOptionalLabel}:</strong> {document.notes}
            </div>
          )}
        </div>

        {/* Admin Access Permissions */}
        {isAdmin && (
          <div className="card">
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 8 }}>
              {strings.whoCanAccessTitle}
            </div>
            {linkedMembers.length <= 1 ? (
              <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                {strings.noOtherLinkedUsersNote}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {linkedMembers.map((m) => {
                  const fixedAdmin = m.linkedUserId === family.adminUid;
                  const checked = fixedAdmin || document.allowedUids.includes(m.linkedUserId);
                  return (
                    <label
                      key={m.id}
                      style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, cursor: fixedAdmin ? 'default' : 'pointer' }}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={fixedAdmin}
                        onChange={(e) => {
                          const next = e.target.checked
                            ? Array.from(new Set([...document.allowedUids, m.linkedUserId]))
                            : document.allowedUids.filter((u) => u !== m.linkedUserId);
                          void onUpdatePermissions(document, next);
                        }}
                      />
                      <span>
                        {m.name} ({m.relation})
                      </span>
                    </label>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {confirmDelete && (
          <div className="modal-backdrop" onClick={() => setConfirmDelete(false)}>
            <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
              <h3 style={{ margin: '0 0 8px' }}>{strings.deleteConfirmTitle}</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>{strings.deleteConfirmMessage}</p>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button type="button" className="btn btn-outline" onClick={() => setConfirmDelete(false)}>
                  {strings.cancel}
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={async () => {
                    setConfirmDelete(false);
                    await onDeleteDocument(document);
                    onClose();
                  }}
                >
                  {strings.confirmDelete}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
