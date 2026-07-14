import { config } from './config';
import { getIdToken } from './auth';

export interface FileItem {
  id: string;
  name: string;
  description: string;
  sizeBytes: number;
  contentType: string;
  status: 'PENDING_UPLOAD' | 'ACTIVE';
  createdAt: string;
  updatedAt: string;
}

export interface FileDetails extends FileItem {
  versions: { s3VersionId: string; sizeBytes: number; createdAt: string }[];
}

export interface UploadTicket {
  fileId: string;
  uploadUrl: string;
}

export interface ShareLinkInfo {
  token: string;
  fileId: string;
  createdAt: string;
  expiresAt: string;
}

class ApiError extends Error {
  constructor(public status: number, public title: string, detail?: string) {
    super(detail ?? title);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = await getIdToken();
  const response = await fetch(config.apiUrl + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    redirect: 'manual', // download endpoints answer 302 → we read Location ourselves
  });

  if (response.status === 204) return undefined as T;
  if (!response.ok && response.type !== 'opaqueredirect') {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(response.status, problem.title ?? response.statusText, problem.detail);
  }
  return (await response.json().catch(() => undefined)) as T;
}

export const api = {
  listFiles: () => request<FileItem[]>('GET', '/files'),
  getFile: (id: string) => request<FileDetails>('GET', `/files/${id}`),
  initiateUpload: (file: File, description: string) =>
    request<UploadTicket>('POST', '/files', {
      name: file.name,
      description,
      sizeBytes: file.size,
      contentType: file.type || 'application/octet-stream',
    }),
  initiateVersion: (id: string, file: File) =>
    request<UploadTicket>('POST', `/files/${id}/versions`, { sizeBytes: file.size }),
  completeUpload: (id: string) => request<FileItem>('POST', `/files/${id}/complete`),
  updateDescription: (id: string, description: string) =>
    request<FileItem>('PATCH', `/files/${id}`, { description }),
  deleteFile: (id: string) => request<void>('DELETE', `/files/${id}`),
  createShare: (id: string, ttlHours: number) =>
    request<ShareLinkInfo>('POST', `/files/${id}/shares`, { ttlHours }),
  revokeShare: (token: string) => request<void>('DELETE', `/shares/${token}`),
  adminListFiles: () => request<FileItem[]>('GET', '/admin/files'),
  adminDeleteFile: (id: string) => request<void>('DELETE', `/admin/files/${id}`),

  shareUrl: (token: string) => `${config.apiUrl}/public/shares/${token}`,

  async downloadUrl(id: string, versionId?: string): Promise<string> {
    const token = await getIdToken();
    const suffix = versionId ? `?versionId=${encodeURIComponent(versionId)}` : '';
    const response = await fetch(`${config.apiUrl}/files/${id}/download${suffix}`, {
      headers: { Authorization: `Bearer ${token}` },
      redirect: 'manual',
    });
    const location = response.headers.get('Location');
    if (location) return location;
    throw new ApiError(response.status, 'Download unavailable');
  },

  uploadToS3(url: string, file: File, onProgress: (percent: number) => void): Promise<void> {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('PUT', url);
      xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100));
      xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`S3 PUT ${xhr.status}`)));
      xhr.onerror = () => reject(new Error('S3 upload failed'));
      xhr.send(file);
    });
  },
};
