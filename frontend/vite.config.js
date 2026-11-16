import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // /api 요청은 스프링(8080)으로 넘김 → 같은 주소라 로그인 쿠키가 그대로 붙음
  server: {
    proxy: { '/api': 'http://localhost:8080' },
    // 11/16 ①: 폰에서 공유 링크를 열 수 있게 터널(ngrok·cloudflared) 주소로 들어와도 막지 않음
    allowedHosts: ['.ngrok-free.app', '.ngrok.app', '.trycloudflare.com'],
  },
})
