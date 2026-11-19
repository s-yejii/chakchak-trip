// 일정 카카오톡 공유 (④ 11/19): ① 공유 링크(/s/코드, 로그인 없이 열림)를 카카오톡 피드로 보내요. 카카오를 못 쓰면 링크 복사
//   여행 중 화면 [일정 공유]와 바뀐 일정 화면 [카카오톡으로 공유]가 같이 써요 (예전엔 바뀐 일정 화면만 가짜 공유 창이었어요)
import { createShareLink } from '@/api/members'
import { shareToKakao } from '@/utils/kakaoShare'

// 공유 링크 앞주소: 폰에서 열리게 터널 주소(frontend/.env.local의 VITE_SHARE_BASE_URL)가 있으면 그것, 없으면 지금 주소
const SHARE_BASE_URL = import.meta.env.VITE_SHARE_BASE_URL || location.origin
// 카톡 카드 사진: 트리피 깃발(800×400, public/img/share-card.png). 카카오는 인터넷 주소 사진만 가져가서
//   카카오 서버에 올려 둔 주소를 기본값으로 써요 (localhost에서도 사진이 나옴). 바꾸려면 .env.local의 VITE_SHARE_IMAGE_URL
const KAKAO_SHARE_IMAGE = 'https://k.kakaocdn.net/dn/b1Y1c5/dJMcacyzq5v/b2FTQQoOkwVRXEqo7MQyA0/kakaolink40_original.png'
const SHARE_IMAGE_URL = import.meta.env.VITE_SHARE_IMAGE_URL || KAKAO_SHARE_IMAGE
const SHARE_IMAGE_WIDTH = 800
const SHARE_IMAGE_HEIGHT = 400

/** 여행 일정을 카카오톡으로 보내기. 'kakao'(카톡 공유 창을 엶) 또는 'copied'(링크를 복사함)를 돌려줘요 */
export async function shareTrip(tripId, title, description, buttonTitle) {
  const link = await createShareLink(tripId)
  return shareToKakao({
    title,
    description,
    imageUrl: SHARE_IMAGE_URL,
    imageWidth: SHARE_IMAGE_WIDTH,
    imageHeight: SHARE_IMAGE_HEIGHT,
    link: SHARE_BASE_URL + link.path,
    buttonTitle,
  })
}
