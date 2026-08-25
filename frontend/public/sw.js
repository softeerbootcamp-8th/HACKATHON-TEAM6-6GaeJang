// 오프라인 캐싱 없음 — PWA 설치 요건(controlled fetch handler) 충족만을 위한 최소 서비스워커.
self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('fetch', () => {
  // 캐싱 없이 네트워크 요청을 그대로 통과시킨다.
})
