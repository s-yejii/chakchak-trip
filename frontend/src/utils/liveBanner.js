// 여행 중 화면 위쪽 알림 줄 (줄을 서듯 차례대로 보여요)
//  - 한 번에 한 장만 보여요. 사용자가 위로 밀거나 닫아야 다음 알림이 떠요 (사용자가 끄기 전에는 안 없애요)
//  - 새 알림은 맨 뒤에 서요. 제목·내용이 완전히 같은 알림은 한 번만 넣어요
//  - 화면을 옮기거나 새로고침해도 남게 sessionStorage에 둬요 (이 탭이 열려 있는 동안만 남는 저장소)
import { reactive } from 'vue'

const STORAGE_KEY = 'live-banner-queue'
// 알림 종류(kind): 서버 알림 key 끝 숫자(남은 분·늦는 분 묶음)는 떼고 같은 종류로 봐요
//   예: "leave-12-3" → "leave-12"
const KIND_WITH_NUMBER = /^(overdue|late|leave|next|tight)-(\d+)-/

function load() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) || []
  } catch {
    return []
  }
}
function save() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(liveBanner.queue))
  } catch {
    // 저장소를 못 쓰면 이 화면에 있는 동안만
  }
}
function kindOf(key) {
  const match = (key || '').match(KIND_WITH_NUMBER)
  return match ? match[1] + '-' + match[2] : key
}

// queue: 차례를 기다리는 알림들 [{ key, title, body, warn, icon, to }]
// isCardOpen: 가운데 카드(놓침 카드)가 떠 있는지. 떠 있으면 시연 [+분] 버튼을 막아요
export const liveBanner = reactive({ queue: load(), isCardOpen: false })

// 알림 줄 맨 뒤에 넣기 (앞 알림을 덮어쓰지 않아요). 제목·내용이 완전히 같으면 안 넣어요
export function showLiveNotice(notice) {
  const item = { ...notice, kind: kindOf(notice.key || notice.title) }
  const isSame = liveBanner.queue.some((queued) => queued.title === item.title && queued.body === item.body)
  if (isSame) return
  liveBanner.queue.push(item)
  save()
}

// 맨 앞 알림 닫기 (사용자가 위로 밀거나 눌렀을 때만) → 다음 알림이 이어서 보임
export function closeLiveNotice() {
  liveBanner.queue.shift()
  save()
}

// isOld가 true인 알림만 빼요
//   예: 같은 예약에 더 큰 알림(늦음·놓침 카드)이 뜨면, 그 예약의 옛 알림("여유 N분"뿐인 것 등)을 빼요
export function removeLiveNotices(isOld) {
  for (let i = liveBanner.queue.length - 1; i >= 0; i--) {
    if (isOld(liveBanner.queue[i])) liveBanner.queue.splice(i, 1)
  }
  save()
}

// 여행이 끝났거나 다른 여행일 때 줄 비우기
export function clearLiveNotices() {
  liveBanner.queue.splice(0)
  save()
}
