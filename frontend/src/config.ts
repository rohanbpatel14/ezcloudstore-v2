// Injected at build time (CI passes the CDK outputs as Vite env vars).
export const config = {
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  cognitoDomain: import.meta.env.VITE_COGNITO_DOMAIN ?? '',
  clientId: import.meta.env.VITE_COGNITO_CLIENT_ID ?? '',
  redirectUri: import.meta.env.VITE_REDIRECT_URI ?? window.location.origin + '/',
};
