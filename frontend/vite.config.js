import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// 세슘 3D (11/19 ⑤, 웹 회고 기본): 세슘 유니티 빌드는 여러 스레드(SharedArrayBuffer)를 써서 페이지가 "격리 모드"여야 함
// → 웹 회고 화면(/web/retro)과, 그 화면이 부르는 파일(Referer가 /web/retro), 세슘 빌드 파일에만 헤더를 붙임 (다른 화면은 그대로)
//   그 페이지가 띄우는 일꾼(worker, 예: MapLibre 지도 일꾼) 파일에도 같은 헤더가 있어야 막히지 않음. 서버는 nginx가 같은 일을 함
// credentialless: 다른 사이트 자료(위성 사진·글꼴 등)를 쿠키 없이 받는 조건으로 허락 (require-corp보다 덜 막음)
function cesiumHeaders() {
  return {
    name: 'cesium-headers',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const referer = req.headers.referer || ''
        if (req.url.startsWith('/web/retro') || referer.includes('/web/retro') || req.url.startsWith('/unity3d-cesium/')) {
          res.setHeader('Cross-Origin-Opener-Policy', 'same-origin')
          res.setHeader('Cross-Origin-Embedder-Policy', 'credentialless')
        }
        next()
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    cesiumHeaders(),
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
