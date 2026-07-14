import { useCallback, useEffect, useState } from 'react';
import { api, FileItem } from '../api';

export function AdminPanel() {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [error, setError] = useState('');

  const refresh = useCallback(() => {
    api.adminListFiles().then(setFiles).catch((e) => setError(e.message));
  }, []);

  useEffect(refresh, [refresh]);

  async function remove(id: string) {
    if (!confirm('Admin delete: remove this file for its owner?')) return;
    await api.adminDeleteFile(id);
    refresh();
  }

  return (
    <div className="card">
      <strong>All files (admin)</strong>
      {error && <p className="error">{error}</p>}
      {files.length === 0 ? (
        <p className="empty">No files in the system.</p>
      ) : (
        files.map((file) => (
          <div key={file.id} className="filerow">
            <div className="meta">
              <div className="name">{file.name}</div>
              <div className="sub">
                {(file.sizeBytes / 1024).toFixed(1)} KB · {file.status} · {new Date(file.updatedAt).toLocaleString()}
              </div>
            </div>
            <div className="actions">
              <button className="btn small danger" onClick={() => remove(file.id)}>
                Delete
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
