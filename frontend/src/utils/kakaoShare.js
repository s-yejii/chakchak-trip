// 카카오톡 공유 (③, 11/08): 바뀐 일정을 카카오톡 피드 메시지로 보내요.
//   키는 서버(GET /api/config/public)의 카카오 JS 키를 써요 (코드에 키를 적지 않음).
//   SDK: 카카오 공식 JavaScript SDK (https://developers.kakao.com/docs/latest/ko/javascript/getting-started)
import { get } from '@/api/http'

const SDK_URL = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.2/kakao.min.js'
let loading = null

function addScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.onload = resolve
    script.onerror = () => reject(new Error('카카오 SDK를 불러오지 못했어요'))
    document.head.appendChild(script)
  })
}

// 카카오 SDK를 한 번만 불러와 키로 초기화. 키가 없으면 false
async function readyKakao() {
  if (!loading) {
    loading = (async () => {
      const config = await get('/config/public')
      if (!config || !config.kakaoJsKey) return false
      if (!window.Kakao) await addScript(SDK_URL)
      if (!window.Kakao.isInitialized()) window.Kakao.init(config.kakaoJsKey)
      return true
    })().catch(() => false)
  }
  return loading
}

// 피드 메시지 보내기: { title, description, imageUrl, imageWidth, imageHeight, link, buttonTitle }. 카카오를 못 쓰면 링크를 복사하고 'copied'
//   buttonTitle은 11/16 ① 상재가 추가 (없으면 예전처럼 '바뀐 일정 보기')
export async function shareToKakao(message) {
  if (await readyKakao()) {
    const link = { mobileWebUrl: message.link, webUrl: message.link }
    window.Kakao.Share.sendDefault({
      objectType: 'feed',
      content: { title: message.title, description: message.description, imageUrl: message.imageUrl,
        imageWidth: message.imageWidth, imageHeight: message.imageHeight, link }, // 사진 크기를 주면 카드에 그 비율로 나와요 (없으면 카카오 기본)
      buttons: [{ title: message.buttonTitle || '바뀐 일정 보기', link }],
    })
    return 'kakao'
  }
  await navigator.clipboard.writeText(message.title + '\n' + message.description + '\n' + message.link)
  return 'copied'
}
