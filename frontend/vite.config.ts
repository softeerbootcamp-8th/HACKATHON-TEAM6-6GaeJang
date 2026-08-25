import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'

export default defineConfig({
  plugins: [
    // routes/ 를 스캔해 routeTree.gen.ts 를 만든다. react 플러그인보다 먼저 와야 한다.
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  server: {
    port: 5173,
    // /api 는 백엔드로 넘긴다 — 브라우저에서는 동일 오리진이라 CORS 이슈가 없다.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // STOMP 핸드셰이크도 동일 오리진으로 넘겨야 세션 쿠키(SameSite=Lax)가 붙는다.
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
})
