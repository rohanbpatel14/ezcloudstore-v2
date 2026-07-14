import { config } from './config';

// Authorization Code + PKCE against the Cognito Hosted UI. No secrets in
// the browser; the verifier lives in sessionStorage only for the round trip.

interface TokenSet {
  id_token: string;
  access_token: string;
  refresh_token?: string;
  expires_at: number; // epoch ms
}

const STORAGE_KEY = 'ezcs.tokens';

function base64Url(bytes: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function randomString(length: number): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return base64Url(bytes.buffer).slice(0, length);
}

export async function login(): Promise<void> {
  const verifier = randomString(64);
  const state = randomString(32);
  sessionStorage.setItem('ezcs.pkce', JSON.stringify({ verifier, state }));

  const challenge = base64Url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
  const params = new URLSearchParams({
    client_id: config.clientId,
    response_type: 'code',
    scope: 'openid email profile',
    redirect_uri: config.redirectUri,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  window.location.assign(`https://${config.cognitoDomain}/oauth2/authorize?${params}`);
}

export async function completeLoginIfCallback(): Promise<boolean> {
  const query = new URLSearchParams(window.location.search);
  const code = query.get('code');
  if (!code) return false;

  const stored = sessionStorage.getItem('ezcs.pkce');
  sessionStorage.removeItem('ezcs.pkce');
  if (!stored) return false;
  const { verifier, state } = JSON.parse(stored);
  if (query.get('state') !== state) throw new Error('OAuth state mismatch');

  const response = await fetch(`https://${config.cognitoDomain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: config.redirectUri,
      code_verifier: verifier,
    }),
  });
  if (!response.ok) throw new Error('Token exchange failed');
  storeTokens(await response.json());
  window.history.replaceState({}, '', config.redirectUri);
  return true;
}

function storeTokens(raw: { id_token: string; access_token: string; refresh_token?: string; expires_in: number }) {
  const tokens: TokenSet = {
    id_token: raw.id_token,
    access_token: raw.access_token,
    refresh_token: raw.refresh_token ?? currentTokens()?.refresh_token,
    expires_at: Date.now() + (raw.expires_in - 60) * 1000,
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
}

function currentTokens(): TokenSet | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as TokenSet) : null;
}

export async function getIdToken(): Promise<string | null> {
  const tokens = currentTokens();
  if (!tokens) return null;
  if (Date.now() < tokens.expires_at) return tokens.id_token;
  if (!tokens.refresh_token) return null;

  const response = await fetch(`https://${config.cognitoDomain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: config.clientId,
      refresh_token: tokens.refresh_token,
    }),
  });
  if (!response.ok) {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
  storeTokens(await response.json());
  return currentTokens()!.id_token;
}

export interface UserClaims {
  sub: string;
  email?: string;
  groups: string[];
}

export function claims(): UserClaims | null {
  const tokens = currentTokens();
  if (!tokens) return null;
  const payload = JSON.parse(atob(tokens.id_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
  return {
    sub: payload.sub,
    email: payload.email,
    groups: payload['cognito:groups'] ?? [],
  };
}

export function signedIn(): boolean {
  return currentTokens() !== null;
}

export function logout(): void {
  localStorage.removeItem(STORAGE_KEY);
  const params = new URLSearchParams({ client_id: config.clientId, logout_uri: config.redirectUri });
  window.location.assign(`https://${config.cognitoDomain}/logout?${params}`);
}
