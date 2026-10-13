import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// In development the API runs on :8080 unless API_TARGET points elsewhere (e.g. http://localhost:8081).
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'League Platform',
        short_name: 'Leagues',
        description: 'Run custom football leagues with your community.',
        // --ink from src/tokens.css; the manifest can't reference CSS variables.
        theme_color: '#1c1c1e',
        background_color: '#1c1c1e',
        display: 'standalone',
        start_url: '/',
        icons: [{ src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml' }],
      },
      workbox: {
        // Sign-in redirects must reach the server, never the app shell.
        navigateFallbackDenylist: [/^\/api\//, /^\/oauth2\//, /^\/login\/oauth2\//],
        // Cache public API reads so standings stay viewable offline (read-only).
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/public/'),
            handler: 'NetworkFirst',
            options: { cacheName: 'public-api', networkTimeoutSeconds: 5 },
          },
        ],
      },
    }),
  ],
  server: {
    // Proxying keeps everything same-origin (no CORS),
    // and the OAuth routes must go through the proxy so the session cookie lands on this host.
    proxy: {
      '/api': { target: apiTarget, xfwd: true },
      '/oauth2': { target: apiTarget, xfwd: true },
      '/login/oauth2': { target: apiTarget, xfwd: true },
    },
  },
})
