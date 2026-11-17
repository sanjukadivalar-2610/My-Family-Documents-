import React, { useEffect, useState } from 'react';
import { Edit2, ImagePlus, ShieldCheck, Trash2, UserPlus } from 'lucide-react';
import { FamilyAccount, FamilyDocument, FamilyMember } from '../domain/models';
import { AppStrings } from '../localization/appStrings';
import { MemberAvatar } from '../components/SharedComponents';

export async function compressImageToBase64(file: File): Promise<string> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('Failed to read photo'));
    reader.readAsDataURL(file);
  });

  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('Invalid image file'));
    image.src = dataUrl;
  });

  const maxDim = 240;
  const scale = Math.min(maxDim / img.width, maxDim / img.height, 1);
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(img.width * scale));
  canvas.height = Math.max(1, Math.round(img.height * scale));
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  const jpegUrl = canvas.toDataURL('image/jpeg', 0.75);
  return jpegUrl.split(',')[1] || '';
}

interface MemberEditorModalProps {
  existing: FamilyMember | null;
  isAdmin: boolean;
  isRootAdmin: boolean;
  onClose: () => void;
  onSave: (params: {
    existing: FamilyMember | null;
    name: string;
    relation: string;
    role: 'admin' | 'member';
    email: string;
    photoBase64?: string | null;
  }) => Promise<void>;
  strings: AppStrings;
}

export function MemberEditorModal({
  existing,
  isAdmin,
  isRootAdmin,
  onClose,
  onSave,
  strings,
}: MemberEditorModalProps) {
  const [name, setName] = useState(existing?.name || '');
  const [relation, setRelation] = useState(existing?.relation || 'Member');
  const [role, setRole] = useState<'admin' | 'member'>(existing?.role || 'member');
  const [email, setEmail] = useState(existing?.email || '');
  const [photoBase64, setPhotoBase64] = useState<string | null>(existing?.photoBase64 || null);
  const [isSaving, setIsSaving] = useState(false);

  const handlePhotoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const compressed = await compressImageToBase64(file);
    if (compressed) setPhotoBase64(compressed);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setIsSaving(true);
    try {
      await onSave({ existing, name, relation, role, email, photoBase64 });
      onClose();
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3 style={{ margin: '0 0 16px', fontSize: 19, fontWeight: 800 }}>
          {existing ? strings.editMember : strings.addMember}
        </h3>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <MemberAvatar name={name || 'M'} photoBase64={photoBase64} size={56} />
            <label className="btn btn-outline" style={{ cursor: 'pointer' }}>
              <ImagePlus size={16} />
              <span>{strings.selectPhoto}</span>
              <input
                type="file"
                accept="image/png,image/jpeg,image/jpg"
                onChange={handlePhotoChange}
                style={{ display: 'none' }}
              />
            </label>
          </div>

          <div>
            <label className="label">{strings.memberNameLabel}</label>
            <input
              className="input"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div>
            <label className="label">{strings.relationLabel}</label>
            <input
              className="input"
              type="text"
              value={relation}
              onChange={(e) => setRelation(e.target.value)}
              required
            />
          </div>

          {isAdmin && !isRootAdmin && (
            <>
              <div>
                <label className="label">{strings.linkedEmailLabel}</label>
                <input
                  className="input"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              <div>
                <label className="label">Role</label>
                <select
                  className="select"
                  value={role}
                  onChange={(e) => setRole(e.target.value === 'admin' ? 'admin' : 'member')}
                >
                  <option value="member">{strings.roleMember}</option>
                  <option value="admin">{strings.roleAdmin}</option>
                </select>
              </div>
            </>
          )}

          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
            <button type="button" className="btn btn-outline" onClick={onClose}>
              {strings.cancel}
            </button>
            <button type="submit" className="btn btn-primary" disabled={!name.trim() || isSaving}>
              {strings.save}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface FamilyPageProps {
  family: FamilyAccount;
  members: FamilyMember[];
  documents: FamilyDocument[];
  currentUserId: string;
  isAdmin: boolean;
  openAddInitially: boolean;
  onConsumeInitialAdd: () => void;
  onSaveMember: (params: {
    existing: FamilyMember | null;
    name: string;
    relation: string;
    role: 'admin' | 'member';
    email: string;
    photoBase64?: string | null;
  }) => Promise<void>;
  onDeleteMember: (memberId: string) => Promise<void>;
  onOpenMemberDocuments: (memberId: string) => void;
  onOpenAdminPanel: () => void;
  strings: AppStrings;
}

export function FamilyPage({
  family,
  members,
  documents,
  currentUserId,
  isAdmin,
  openAddInitially,
  onConsumeInitialAdd,
  onSaveMember,
  onDeleteMember,
  onOpenMemberDocuments,
  onOpenAdminPanel,
  strings,
}: FamilyPageProps) {
  const [editingMember, setEditingMember] = useState<FamilyMember | null | undefined>(undefined);
  const [pendingDelete, setPendingDelete] = useState<FamilyMember | null>(null);

  useEffect(() => {
    if (openAddInitially) {
      if (isAdmin) setEditingMember(null);
      onConsumeInitialAdd();
    }
  }, [openAddInitially, isAdmin, onConsumeInitialAdd]);

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
        <div>
          <h1 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>{strings.familyMembersTitle}</h1>
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            {family.familyName} • {members.length} {strings.memberCountSuffix}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {isAdmin && (
            <>
              <button type="button" className="btn btn-outline" onClick={onOpenAdminPanel}>
                <ShieldCheck size={16} />
                <span>{strings.adminConsoleButton}</span>
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setEditingMember(null)}
              >
                <UserPlus size={16} />
                <span>{strings.addMember}</span>
              </button>
            </>
          )}
        </div>
      </div>

      {!isAdmin && (
        <div
          style={{
            background: 'var(--sky-soft)',
            color: '#172554',
            padding: 14,
            borderRadius: 14,
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          {strings.adminOnlyManageNote}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {members.map((member) => {
          const docCount = documents.filter((d) => d.memberId === member.id).length;
          const canEdit = isAdmin || member.linkedUserId === currentUserId;
          const canDelete = isAdmin && member.linkedUserId !== family.adminUid;

          return (
            <div
              key={member.id}
              className="card"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                padding: 14,
                cursor: 'pointer',
              }}
              onClick={() => onOpenMemberDocuments(member.id)}
            >
              <MemberAvatar name={member.name} photoBase64={member.photoBase64} size={52} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <span style={{ fontWeight: 800, fontSize: 16 }}>{member.name}</span>
                  <span
                    className="pill"
                    style={{
                      background: member.role === 'admin' ? '#172554' : 'var(--sky-soft)',
                      color: member.role === 'admin' ? '#38BDF8' : '#172554',
                    }}
                  >
                    {member.role === 'admin' ? strings.roleAdmin : strings.roleMember}
                  </span>
                </div>
                <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
                  {member.relation} • {docCount} {strings.navDocuments}
                </div>
                {member.email && (
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{member.email}</div>
                )}
              </div>

              <div
                style={{ display: 'flex', gap: 6 }}
                onClick={(e) => e.stopPropagation()}
              >
                {canEdit && (
                  <button
                    type="button"
                    className="btn btn-outline"
                    style={{ minHeight: 40, padding: '8px 10px' }}
                    aria-label={strings.editMember}
                    onClick={() => setEditingMember(member)}
                  >
                    <Edit2 size={16} />
                  </button>
                )}
                {canDelete && (
                  <button
                    type="button"
                    className="btn btn-danger"
                    style={{ minHeight: 40, padding: '8px 10px' }}
                    aria-label={strings.removeMember}
                    onClick={() => setPendingDelete(member)}
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {editingMember !== undefined && (
        <MemberEditorModal
          existing={editingMember}
          isAdmin={isAdmin}
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
