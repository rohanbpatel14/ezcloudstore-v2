import { useCallback, useEffect, useRef, useState } from 'react';
import { api, FileDetails, FileItem, ShareLinkInfo } from '../api';

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

export function Dashboard() {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [details, setDetails] = useState<FileDetails | null>(null);
  const [share, setShare] = useState<ShareLinkInfo | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const versionTarget = useRef<string | null>(null);

  const refresh = useCallback(() => {
    api.listFiles().then(setFiles).catch((e) => setError(e.message));
  }, []);

  useEffect(refresh, [refresh]);

  async function handleChosenFile(chosen: File) {
    setError('');
    setProgress(0);
    try {
      const targetId = versionTarget.current;
      const ticket = targetId
        ? await api.initiateVersion(targetId, chosen)
        : await api.initiateUpload(chosen, '');
      await api.uploadToS3(ticket.uploadUrl, chosen, setProgress);
      await api.completeUpload(ticket.fileId);
      refresh();
      if (expanded === targetId) openDetails(targetId!);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setProgress(null);
      versionTarget.current = null;
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  function pickFile(forVersionOf?: string) {
    versionTarget.current = forVersionOf ?? null;
    fileInput.current?.click();
  }

  async function openDetails(id: string) {
    if (expanded === id) {
      setExpanded(null);
      setDetails(null);
      setShare(null);
      return;
    }
    setExpanded(id);
    setShare(null);
    setDetails(await api.getFile(id));
  }

  async function download(id: string, versionId?: string) {
    try {
      window.open(await api.downloadUrl(id, versionId), '_blank');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Download failed');
    }
  }

  async function remove(id: string) {
    if (!confirm('Delete this file and all its versions?')) return;
    await api.deleteFile(id);
    setExpanded(null);
    refresh();
  }

  async function createShare(id: string) {
    setShare(await api.createShare(id, 24));
  }

  async function editDescription(file: FileItem) {
    const next = prompt('Description', file.description);
    if (next === null) return;
    await api.updateDescription(file.id, next);
    refresh();
    if (expanded === file.id) setDetails(await api.getFile(file.id));
  }

  return (
    <>
      <input
        ref={fileInput}
        type="file"
        hidden
        onChange={(e) => e.target.files?.[0] && handleChosenFile(e.target.files[0])}
      />

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <strong>Files</strong>
          <button className="btn primary" onClick={() => pickFile()} disabled={progress !== null}>
            {progress !== null ? `Uploading ${progress}%` : 'Upload file'}
          </button>
        </div>
        {progress !== null && (
          <div className="progress">
            <div style={{ width: `${progress}%` }} />
          </div>
        )}
        {error && <p className="error">{error}</p>}

        {files.length === 0 ? (
          <p className="empty">No files yet — upload your first (up to 10 MB).</p>
        ) : (
          files.map((file) => (
            <div key={file.id}>
              <div className="filerow">
                <div className="meta">
                  <div className="name">{file.name}</div>
                  <div className="sub">
                    {formatSize(file.sizeBytes)} · {file.description || 'no description'} ·{' '}
                    {formatDate(file.updatedAt)}
                    {file.status === 'PENDING_UPLOAD' && ' · upload incomplete'}
                  </div>
                </div>
                <div className="actions">
                  <button className="btn small" onClick={() => download(file.id)}>
                    Download
                  </button>
                  <button className="btn small" onClick={() => openDetails(file.id)}>
                    {expanded === file.id ? 'Close' : 'Details'}
                  </button>
                  <button className="btn small danger" onClick={() => remove(file.id)}>
                    Delete
                  </button>
                </div>
              </div>

              {expanded === file.id && details && (
                <div className="detail">
                  <div className="actions" style={{ marginBottom: 10, display: 'flex', gap: 6 }}>
                    <button className="btn small" onClick={() => pickFile(file.id)}>
                      Upload new version
                    </button>
                    <button className="btn small" onClick={() => editDescription(file)}>
                      Edit description
                    </button>
                    <button className="btn small" onClick={() => createShare(file.id)}>
                      Share (24h link)
                    </button>
                  </div>

                  {share && (
                    <p>
                      Share link (expires {formatDate(share.expiresAt)}):{' '}
                      <code>{api.shareUrl(share.token)}</code>{' '}
                      <button
                        className="btn small"
                        onClick={() => navigator.clipboard.writeText(api.shareUrl(share.token))}
                      >
                        Copy
                      </button>{' '}
                      <button className="btn small danger" onClick={() => api.revokeShare(share.token).then(() => setShare(null))}>
                        Revoke
                      </button>
                    </p>
                  )}

                  <h4>Versions</h4>
                  <ul>
                    {details.versions.map((v) => (
                      <li key={v.s3VersionId}>
                        {formatDate(v.createdAt)} · {formatSize(v.sizeBytes)}{' '}
                        <button className="btn small" onClick={() => download(file.id, v.s3VersionId)}>
                          Download
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </>
  );
}
