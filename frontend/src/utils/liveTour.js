// 여행 중 화면 첫 안내 (④): 처음 여행을 시작한 사람에게 트리피가 버튼을 하나씩 짚어 줘요 (LiveTour.vue)
//  본 적이 있는지는 이 브라우저(localStorage)에만 남겨요.
//  시크릿 창처럼 저장이 막히면 오류가 날 수 있어서 try로 감싸요 (그럴 땐 매번 뜰 수 있어요)
//  다시 보고 싶으면 주소 끝에 ?tour=1 (시연·촬영용)

const DONE_KEY = 'tripbiseo.liveTour.v1'

// 안내 단계 목록. 단계마다:
//   target: 짚을 요소의 data-tour 값 (null이면 가운데 인사 카드)
//   shape: 짚는 구멍 모양 (pill 알약 / circle 동그라미 / card 둥근 네모)
//   demoNoti: 진짜 알림이 없어도 이 단계에서만 안내용 가짜 지연 알림을 위에 띄워요 (문구는 서버 LiveAlertMaker와 같은 말투)
export const TOUR_STEPS = [
  { target: null, title: '여행 시작!', text: '트리피가 화면을 빠르게 알려 줄게' },
  { target: 'noti', shape: 'card', demoNoti: true, title: '출발 알림', text: '늦기 전에 위에서 먼저 알려 줄게' },
  { target: 'sheet', shape: 'card', title: '오늘 일정', text: '다음 갈 곳과 도착 시간이 여기 떠' },
  { target: 'ask', shape: 'pill', title: '어디 갈까?', text: "'조용한 카페'처럼 물어보면 찾아 줄게" },
  { target: 'pay', shape: 'pill', title: '결제 메모', text: '영수증 찍으면 어디서 얼마 썼는지 정리해 줘' },
  { target: 'fab', shape: 'circle', title: '더 보기', text: '링크 추가·내 일정·공유는 여기' },
]

/** 이 브라우저에서 안내를 끝까지 봤거나 건너뛰었는지 */
export function isTourDone() {
  try {
    return localStorage.getItem(DONE_KEY) === '1'
  } catch {
    return false
  }
}

/** 다시 안 뜨게 표시 */
export function markTourDone() {
  try {
    localStorage.setItem(DONE_KEY, '1')
  } catch {
    // 저장이 막힌 브라우저면 다음에 또 뜰 뿐이라 그냥 둠
  }
}
