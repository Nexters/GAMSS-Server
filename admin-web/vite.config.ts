import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 로컬 개발: /api 요청을 로컬 백엔드(:8080)로 프록시한다.
  // 브라우저 입장에선 same-origin이라 CORS가 생기지 않는다(배포의 nginx와 같은 구조).
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
