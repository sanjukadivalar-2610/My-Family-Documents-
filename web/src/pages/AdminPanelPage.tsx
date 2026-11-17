import React, { useState } from 'react';
import {
  ArrowLeft,
  CloudUpload,
  Copy,
  Edit2,
  Lock,
  ShieldCheck,
  Trash2,
  UserPlus,
} from 'lucide-react';
import {
  BackupStatusInfo,
  FamilyAccount,
  FamilyDocument,
  FamilyMember,
  formatDateMillis,
} from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { MemberAvatar, SyncStatusBadge } from '../components/SharedComponents';
import { MemberEditorModal } from './FamilyPage';

interface AdminPanelPageProps {
  family: FamilyAccount;
  members: FamilyMember[];
  documents: FamilyDocument[];
  backupStatus: BackupStatusInfo;
  onBack: () => void;
  onRenameFamily: (newName: string) => Promise<void>;
  onCopyFamilyId: (code: string) => void;
  onSaveMember: (params: {
    existing: FamilyMember | null;
    name: string;
    relation: string;
    role: 'admin' | 'member';
    email: string;
    photoBase64?: string | null;
  }) => Promise<void>;
  onDeleteMember: (memberId: string) => Promise<void>;
  onUpdatePermissions: (doc: FamilyDocument, allowedUids: string[]) => Promise<void>;
  onManualBackup: () => Promise<void>;
  strings: AppStrings;
}

export function AdminPanelPage({
  family,
  members,
  documents,
  backupStatus,
  onBack,
  onRenameFamily,
  onCopyFamilyId,
  onSaveMember,
  onDeleteMember,
  onUpdatePermissions,
  onManualBackup,
  strings,
}: AdminPanelPageProps) {
  const [familyNameInput, setFamilyNameInput] = useState(family.familyName);
  const [savingFamilyName, setSavingFamilyName] = useState(false);
  const [editingMember, setEditingMember] = useState<FamilyMember | null | undefined>(undefined);
  const [pendingDelete, setPendingDelete] = useState<FamilyMember | null>(null);
  const [backingUp, setBackingUp] = useState(false);

  const linkedMembers = members.filter((m) => Boolean(m.linkedUserId));

  const handleRenameSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (familyNameInput.trim().length < 2) return;
    setSavingFamilyName(true);
    try {
      await onRenameFamily(familyNameInput);
    } finally {
      setSavingFamilyName(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <button
          type="button"
          className="btn btn-outline"
          style={{ minHeight: 44, padding: '8px 12px' }}
          onClick={onBack}
          aria-label={strings.close}
        >
          <ArrowLeft size={18} />
        </button>
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800 }}>{strings.adminPanelTitle}</h1>
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>{family.familyName}</div>
        </div>
        <span className="pill" style={{ background: '#172554', color: '#38BDF8', padding: '6px 12px' }}>
          <ShieldCheck size={14} />
          <span>{strings.roleAdmin}</span>
        </span>
      </div>

      {/* Family Account Section */}
      <div className="card">
        <div style={{ fontWeight: 800, fontSize: 16, marginBottom: 12 }}>
          {strings.familyAccountSection}
        </div>
        <form
          onSubmit={handleRenameSubmit}
          style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}
        >
          <div style={{ flex: 1, minWidth: 220 }}>
            <label className="label">{strings.familyNameLabel}</label>
            <input
              className="input"
              type="text"
              value={familyNameInput}
              onChange={(e) => setFamilyNameInput(e.target.value)}
              minLength={2}
              required
            />
          </div>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={savingFamilyName || familyNameInput.trim().length < 2}
          >
            {strings.renameFamily}
          </button>
        </form>

        <div
          style={{
            marginTop: 14,
            paddingTop: 12,
            borderTop: '1px solid var(--border-color)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 10,
          }}
        >
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 700 }}>
              {strings.familyIdPrefix}
            </div>
            <div style={{ fontSize: 16, fontWeight: 800, letterSpacing: '0.04em' }}>
              {family.familyCode}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{strings.familyIdHint}</div>
          </div>
          <button
            type="button"
            className="btn btn-outline"
            onClick={() => onCopyFamilyId(family.familyCode)}
          >
            <Copy size={15} />
            <span>{strings.copyFamilyId}</span>
          </button>
        </div>
      </div>

      {/* Manage Members */}
      <div className="card">
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 10,
            marginBottom: 12,
          }}
        >
          <div style={{ fontWeight: 800, fontSize: 16 }}>
            {strings.familyMembersTitle} ({members.length})
          </div>
          <button type="button" className="btn btn-primary" onClick={() => setEditingMember(null)}>
            <UserPlus size={16} />
            <span>{strings.addMember}</span>
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {members.map((member) => {
            const canDelete = member.linkedUserId !== family.adminUid;
            return (
              <div
                key={member.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: 12,
                  borderRadius: 14,
                  background: 'var(--bg-surface-variant)',
                }}
              >
                <MemberAvatar name={member.name} photoBase64={member.photoBase64} size={44} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 15 }}>
                    {member.name}{' '}
                    <span
                      className="pill"
                      style={{
                        background: member.role === 'admin' ? '#172554' : 'var(--sky-soft)',
                        color: member.role === 'admin' ? '#38BDF8' : '#172554',
                        marginLeft: 6,
                      }}
                    >
                      {member.role === 'admin' ? strings.roleAdmin : strings.roleMember}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {member.relation}
                    {member.email ? ` • ${member.email}` : ''}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-outline"
                  style={{ minHeight: 38, padding: '6px 10px' }}
                  onClick={() => setEditingMember(member)}
                  aria-label={strings.editMember}
                >
                  <Edit2 size={15} />
                </button>
                {canDelete && (
                  <button
                    type="button"
                    className="btn btn-danger"
                    style={{ minHeight: 38, padding: '6px 10px' }}
                    onClick={() => setPendingDelete(member)}
                    aria-label={strings.removeMember}
                  >
                    <Trash2 size={15} />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Document Permissions Control */}
      <div className="card">
        <div style={{ fontWeight: 800, fontSize: 16, marginBottom: 8 }}>
          {strings.documentPermissionsSection}
        </div>
        {linkedMembers.length <= 1 ? (
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>{strings.noOtherLinkedUsersNote}</div>
        ) : documents.length === 0 ? (
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>{strings.emptyDocumentsTitle}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {documents.map((doc) => (
              <div
                key={doc.id}
                style={{
                  padding: 12,
                  borderRadius: 14,
                  border: '1px solid var(--border-color)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <Lock size={15} color="#38BDF8" />
                  <span style={{ fontWeight: 700, fontSize: 14 }}>{doc.title}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    ({doc.memberName})
                  </span>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                  {linkedMembers.map((m) => {
                    const fixed = m.linkedUserId === family.adminUid || m.linkedUserId === doc.uploadedByUid;
                    const checked = fixed || doc.allowedUids.includes(m.linkedUserId);
                    return (
                      <label
                        key={m.id}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 6,
                          fontSize: 13,
                          padding: '6px 10px',
                          borderRadius: 10,
                          background: 'var(--bg-surface-variant)',
                          cursor: fixed ? 'default' : 'pointer',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          disabled={fixed}
                          onChange={(e) => {
                            const next = e.target.checked
                              ? Array.from(new Set([...doc.allowedUids, m.linkedUserId]))
                              : doc.allowedUids.filter((u) => u !== m.linkedUserId);
                            void onUpdatePermissions(doc, next);
                          }}
                        />
                        <span>{m.name}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Backup Health */}
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
          <div style={{ fontWeight: 800, fontSize: 16 }}>{strings.backupHealthSection}</div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>
            {strings.lastBackupLabel}{' '}
            {backupStatus.lastBackupAtMillis > 0
              ? formatDateMillis(backupStatus.lastBackupAtMillis)
              : strings.neverBackedUp}{' '}
            • {backupStatus.backedUpDocs}/{backupStatus.totalDocs}
          </div>
          <div style={{ marginTop: 8 }}>
            <SyncStatusBadge syncState={backupStatus.syncState} strings={strings} />
          </div>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          disabled={!backupStatus.isOnline || backingUp}
          onClick={async () => {
            setBackingUp(true);
            try {
              await onManualBackup();
            } finally {
              setBackingUp(false);
            }
          }}
        >
          <CloudUpload size={16} />
          <span>{strings.manualBackupNow}</span>
        </button>
      </div>

      {editingMember !== undefined && (
        <MemberEditorModal
          existing={editingMember}
          isAdmin={true}
          isRootAdmin={editingMember?.linkedUserId === family.adminUid}
          onClose={() => setEditingMember(undefined)}
          onSave={onSaveMember}
          strings={strings}
        />
      )}

      {pendingDelete && (
        <div className="modal-backdrop" onClick={() => setPendingDelete(null)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: '0 0 8px' }}>{strings.removeMember}</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>
              {pendingDelete.name}: {strings.removeMemberConfirm}
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button type="button" className="btn btn-outline" onClick={() => setPendingDelete(null)}>
                {strings.cancel}
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={async () => {
                  const id = pendingDelete.id;
                  setPendingDelete(null);
                  await onDeleteMember(id);
                }}
              >
                {strings.confirmDelete}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
