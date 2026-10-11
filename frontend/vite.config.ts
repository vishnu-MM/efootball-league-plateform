import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'League Platform',
        short_name: 'Leagues',
        description: 'Run custom football leagues with your community.',
        theme_color: '#0b6e4f',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [{ src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml' }],
      },
      workbox: {
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
    // In development the API runs on :8080; proxying keeps everything same-origin (no CORS).
    proxy: { '/api': 'http://localhost:8080' },
  },
})
