// 칩 줄 옆으로 넘기기 (④ 11/16). AI 채팅(AskPanel)·근처 찾기(NearSearch)가 같이 써요. 11/18: AI 채팅 결과 카드 줄도
//  폰 손가락은 브라우저가 원래 옆으로 넘겨 줘요. 여기는 PC 마우스용:
//  - 휠(위아래)을 굴려도 옆으로 (줄 끝에 닿으면 대화창이 위아래로 움직이게 놓아 줌)
//  - 마우스로 잡고 끌어도 옆으로 (끌고 손을 뗄 때 칩이 눌리지 않게 isJustDragged로 막음)
//    끄는 동안은 is-dragging 클래스를 붙여 카드 줄의 칸 맞춤(scroll-snap)을 잠시 끔 (안 끄면 끌 때마다 칸으로 튐)

const DRAG_START_PX = 4      // 이만큼은 움직여야 끌기로 봄 (살짝 흔들린 클릭은 그냥 클릭)
const CLICK_BLOCK_MS = 100   // 끌기를 끝낸 뒤 이 시간 안의 클릭은 무시

let drag = null
let dragEndedAt = 0

/** @wheel: 휠 위아래 → 칩 줄 옆으로 */
export function scrollSideways(event) {
  const box = event.currentTarget
  const isSideways = Math.abs(event.deltaX) > Math.abs(event.deltaY) // 트랙패드로 이미 옆으로 미는 중이면 그대로
  if (box.scrollWidth <= box.clientWidth || isSideways) return
  const isAtStart = event.deltaY < 0 && box.scrollLeft <= 0
  const isAtEnd = event.deltaY > 0 && box.scrollLeft + box.clientWidth >= box.scrollWidth - 1
  if (isAtStart || isAtEnd) return // 더 갈 곳이 없으면 휠을 대화창에 넘김
  box.scrollLeft += event.deltaY
  event.preventDefault() // 대화창·패널이 위아래로 같이 움직이지 않게
}

/** @pointerdown: 마우스로 잡고 끌기 시작 */
export function startChipDrag(event) {
  if (event.pointerType !== 'mouse' || event.button !== 0) return
  const box = event.currentTarget
  drag = { box, startX: event.clientX, startLeft: box.scrollLeft, isMoved: false }
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', endDrag, { once: true })
}

function moveDrag(event) {
  const moved = event.clientX - drag.startX
  if (!drag.isMoved && Math.abs(moved) < DRAG_START_PX) return
  drag.isMoved = true
  drag.box.classList.add('is-dragging')
  drag.box.scrollLeft = drag.startLeft - moved // 손을 오른쪽으로 끌면 줄은 왼쪽으로
}

function endDrag() {
  window.removeEventListener('pointermove', moveDrag)
  if (drag && drag.isMoved) {
    dragEndedAt = Date.now()
    drag.box.classList.remove('is-dragging') // 칸 맞춤이 다시 켜지면서 가까운 카드에 맞춰짐
  }
  drag = null
}

/** 칩 @click에서 먼저 확인: 방금 끌기를 끝낸 거면 true (누른 걸로 안 봄) */
export function isJustDragged() {
  return Date.now() - dragEndedAt < CLICK_BLOCK_MS
}
