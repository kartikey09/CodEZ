import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// Three Spring Boot services back this SPA:
//   auth-service  → /auth/*   (default :8081)
//   contest-api   → /api/*    (default :8080)
//   realtime      → /ws       (default :8083, WebSocket)
// We proxy all three so the browser sees a single origin (http://localhost:5173).
// That keeps the `sid` session cookie first-party (no CORS, no SameSite issues).
// Override targets with VITE_AUTH_ORIGIN / VITE_API_ORIGIN / VITE_WS_ORIGIN if your ports differ.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const authOrigin = env.VITE_AUTH_ORIGIN || 'http://localhost:8081'
  const apiOrigin = env.VITE_API_ORIGIN || 'http://localhost:8080'
  const wsOrigin = env.VITE_WS_ORIGIN || 'ws://localhost:8083'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': path.resolve(__dirname, './src') },
    },
    server: {
      host: true,
      port: 5173,
      proxy: {
        '/auth': { target: authOrigin, changeOrigin: true },
        '/api': { target: apiOrigin, changeOrigin: true },
        '/ws': { target: wsOrigin, ws: true, changeOrigin: true },
      },
    },
  }
})
