import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

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
        theme_color: '#1e7a4f',
        background_color: '#f6f7f4',
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
    // In development the API runs on :8080; proxying keeps everything same-origin (no CORS),
    // and the OAuth routes must go through the proxy so the session cookie lands on this host.
    proxy: {
      '/api': { target: 'http://localhost:8080', xfwd: true },
      '/oauth2': { target: 'http://localhost:8080', xfwd: true },
      '/login/oauth2': { target: 'http://localhost:8080', xfwd: true },
    },
  },
})
