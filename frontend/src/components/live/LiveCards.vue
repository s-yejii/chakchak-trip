<script setup>
import { toDuration } from '@/utils/time'
// 여행 중 화면 아래 시트의 일정 카드 줄 (옆으로 넘겨 보기)
// 카드 사이에는 이동 칩("도보 12분" 같은 작은 버튼)이 있어요
// cards: [{ no, seq, img, title, name, sub, was, subCls, state: 'done'|'now'|'', fixed, leg, legState }]
//   state: 'done' 다녀옴, 'now' 지금 여기, '' 아직 안 감
//   leg: 앞 장소에서 오는 이동 { mode, min, dist }
//   legState: 'past' 지나간 구간, 'now' 지금 이동할 구간, '' 그 밖
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { modes } from '@/data/places'

const props = defineProps({
  cards: { type: Array, required: true },
  selected: { type: Number, default: 0 },
  autoFocus: { type: Boolean, default: false }, // 시연 계정(개발자 모드)에서 켬: 지금 장소·다음 갈 곳이 바뀌면 그 카드로 자동으로 넘겨요
  focus: { type: Object, default: null },       // 지도 핀을 누르면 { no, at } → 그 카드를 가운데로 (at = 누른 시각, 같은 핀을 또 눌러도 값이 바뀌게)
  showEnd: { type: Boolean, default: false },   // 맨 끝에 "여행 끝내기" 카드
  isDayEnd: { type: Boolean, default: false },  // 여러 날 여행의 마지막 날이 아니면 끝 카드를 "내일 계속해요"로
})
const emit = defineEmits(['pick', 'seg', 'scrolled', 'end'])

const box = ref(null)
let movedByCode = false // 처음 가운데 맞출 때는 선택을 바꾸지 않아요

// 다른 카드를 보다가 손을 떼고 3초가 지나면 지금 있는 곳 카드로 돌아와요
const BACK_TO_NOW_MS = 3000
const RETURN_SCROLL_MS = 700 // 부드럽게 돌아가는 동안(0.7초)은 스크롤을 사용자 조작으로 보지 않음
let backTimer = null
let isReturning = false
let isHeld = false // 카드를 누르거나 핀으로 가운데 맞춘 뒤에는, 다시 손으로 넘기기 전까지 지금 카드로 안 돌아감

function nowCardLeft() {
  const el = box.value && box.value.querySelector('.hcard.is-now')
  if (!el) return null
  return el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2
}

function scheduleBackToNow() {
  if (isHeld) return
  clearTimeout(backTimer)
  backTimer = setTimeout(scrollBackToNow, BACK_TO_NOW_MS)
}

function scrollBackToNow() {
  if (drag) return scheduleBackToNow() // 아직 끄는 중이면 다시 기다림
  const left = nowCardLeft()
  if (left === null || Math.abs(box.value.scrollLeft - left) < 4) return
  isReturning = true
  box.value.scrollTo({ left: left, behavior: 'smooth' })
  setTimeout(() => {
    isReturning = false
    const nowCard = props.cards.find((card) => card.state === 'now')
    if (nowCard) emit('scrolled', nowCard.no) // 지도 핀 강조도 지금 카드로
  }, RETURN_SCROLL_MS)
}

// 옆으로 넘기면 가운데에 온 카드 번호를 알려 줘요 (지도 핀 강조용)
function onScroll() {
  if (movedByCode) {
    movedByCode = false
    return
  }
  if (isReturning) return
  scheduleBackToNow()
  const mid = box.value.scrollLeft + box.value.clientWidth / 2
  const cardEls = box.value.querySelectorAll('.hcard[data-no]') // 끝내기 카드(번호 없음)는 빼고 일정 카드만
  let best = 0
  let bestGap = Infinity
  cardEls.forEach((el, i) => {
    const gap = Math.abs(el.offsetLeft + el.offsetWidth / 2 - mid)
    if (gap < bestGap) {
      bestGap = gap
      best = i
    }
  })
  emit('scrolled', props.cards[best].no)
}

// 마우스로 끌어서 옆으로 넘기기 (휴대폰 손가락은 브라우저가 알아서 넘겨 줌)
const DRAG_START_PX = 5
let drag = null // { startX, startScroll, isMoved }

function onPointerDown(e) {
  isHeld = false // 손을 대면 다시 3초 뒤 돌아가기
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  e.preventDefault() // 끄는 동안 글자가 파랗게 선택되지 않게 (클릭은 그대로 됨)
  drag = { startX: e.clientX, startScroll: box.value.scrollLeft, isMoved: false }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  const moved = e.clientX - drag.startX
  if (!drag.isMoved && Math.abs(moved) < DRAG_START_PX) return
  if (!drag.isMoved) {
    drag.isMoved = true
    box.value.style.scrollSnapType = 'none' // 끄는 동안은 카드에 딱 붙지 않게
    box.value.style.cursor = 'grabbing'
  }
  box.value.scrollLeft = drag.startScroll - moved
}
function onPointerUp() {
  removeListeners()
  if (!drag) return
  const wasMoved = drag.isMoved
  drag = null
  box.value.style.cursor = ''
  if (!wasMoved) return
  box.value.style.scrollSnapType = '' // 놓으면 가까운 카드에 붙음
  // 끈 뒤 손을 뗀 자리의 카드가 눌리지 않게 잠깐 막음
  const block = (event) => {
    event.stopPropagation()
    event.preventDefault()
  }
  box.value.addEventListener('click', block, true)
  setTimeout(() => box.value && box.value.removeEventListener('click', block, true), 60)
}
function removeListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}
onUnmounted(() => {
  removeListeners()
  clearTimeout(backTimer)
})

// 마우스 휠(위아래)도 옆으로 넘기기
function onWheel(e) {
  isHeld = false
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return // 트랙패드 옆 밀기는 그대로
  e.preventDefault()
  box.value.scrollLeft += e.deltaY
}

// 도착해서 지금 장소가 바뀌거나, 떠나서 다음 갈 곳이 바뀌면 그 카드로 자동으로 넘겨요
// 지금 있는 곳(now)이 있으면 그 카드, 이동 중이면 아직 안 간 첫 카드
const focusNo = computed(() => {
  const nowCard = props.cards.find((card) => card.state === 'now')
  if (nowCard) return nowCard.no
  const nextCard = props.cards.find((card) => card.state !== 'done')
  return nextCard ? nextCard.no : null
})
watch(focusNo, async (no, oldNo) => {
  if (!props.autoFocus || no == null || no === oldNo || drag) return
  await nextTick() // 카드 모양(지금·다녀옴)이 화면에 바뀐 뒤에 위치를 재요
  centerCard(no, () => {
    // 도착한 곳이면 지도 핀 강조도 그 카드로. 이동 중(다음 갈 곳)은 지도가 내 위치를 따라가니 알리지 않음
    if (props.cards.some((card) => card.no === no && card.state === 'now')) emit('scrolled', no)
  })
})

// 지도 핀을 누르면 그 카드를 가운데로 옮겨요
//   selected 대신 focus만 봐요 (selected는 손으로 넘길 때도 바뀌어서, 넘기는 중에 카드가 끌려가요)
watch(() => props.focus, async (focus) => {
  if (!focus || drag) return
  isHeld = true
  clearTimeout(backTimer)
  await nextTick()
  centerCard(focus.no, null)
})

// 카드 하나를 부드럽게 가운데로 옮기고, 다 옮기면 done을 불러요
//   옮기는 동안은 사용자가 넘긴 걸로 보지 않아요 (3초 뒤 돌아가기를 안 함)
function centerCard(no, done) {
  const el = box.value && box.value.querySelector('.hcard[data-no="' + no + '"]')
  if (!el) return
  isReturning = true
  box.value.scrollTo({ left: el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2, behavior: 'smooth' })
  setTimeout(() => {
    isReturning = false
    if (done) done()
  }, RETURN_SCROLL_MS)
}

// 카드를 누르면 그 카드를 가운데로 옮기고 부모에게 알려요
function onCardClick(card) {
  isHeld = true
  clearTimeout(backTimer)
  centerCard(card.no, null)
  emit('pick', card)
}

// 카드 상태 줄 앞 아이콘: 주의·문 닫음은 경고, 지금 여기는 핀, 다녀옴은 체크, 나머지는 시계
function subIcon(card) {
  if (card.subCls === 'c-warning' || card.subCls === 'c-danger') return 'i-warning'
  if (card.state === 'now') return 'i-pin'
  if (card.state === 'done') return 'i-check'
  return 'i-clock'
}

// 처음 열 때 지금 있는 곳 카드를 가운데로
onMounted(() => {
  const el = box.value.querySelector('.hcard.is-now') || box.value.querySelector('.hcard.is-sel')
  if (el) {
    movedByCode = true
    box.value.scrollLeft = el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2
  }
})
</script>

<template>
  <div ref="box" class="hscroll live-cards" aria-label="일정, 옆으로 넘겨 보기 (지난 일정은 왼쪽)" @scroll.passive="onScroll" @pointerdown="onPointerDown" @wheel="onWheel" @dragstart.prevent>
    <template v-for="card in cards" :key="card.no">
      <span v-if="card.leg" class="segw" :class="card.legState ? 'segw--' + card.legState : ''">
        <button
          type="button"
          class="segc"
          :class="card.legState ? 'segc--' + card.legState : ''"
          :aria-label="`${modes[card.leg.mode].label} ${toDuration(card.leg.min)}, ${card.leg.dist}, 길찾기`"
          @click="emit('seg', card)"
        >
          <!-- 이동 칩 글자는 수단 아이콘 + 시간만 (거리는 화면 읽기용 이름 aria-label에) -->
          <b class="tn"><i class="ic ic--s" :class="modes[card.leg.mode].icon" aria-hidden="true"></i>{{ toDuration(card.leg.min) }}<i class="ic segc__more i-down" aria-hidden="true"></i></b>
        </button>
      </span>
      <button
        type="button"
        class="hcard"
        :data-no="card.no"
        :class="{ 'is-done': card.state === 'done', 'is-now': card.state === 'now', 'is-sel': card.no === selected }"
        @click="onCardClick(card)"
      >
        <span v-if="card.state === 'done'" class="hcard__tag"><i class="ic ic--s i-check" aria-hidden="true"></i>다녀옴</span>
        <span v-if="card.seq != null" class="hcard__no tn" aria-hidden="true">{{ card.seq }}</span>
        <img :src="card.img" alt="" />
        <span class="hcard__body">
          <span class="hcard__name" :title="card.title">
            <i v-if="card.fixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i><span class="hcard__name-t">{{ card.name || card.title }}</span>
          </span>
          <span class="hcard__sub tn" :class="card.subCls"><i class="ic" :class="subIcon(card)" aria-hidden="true"></i><span class="hcard__sub-t"><template v-if="card.was"><s class="hcard__was" :aria-label="'원래 ' + card.was">{{ card.was }}</s> </template>{{ card.sub }}</span></span>
        </span>
      </button>
    </template>
    <!-- 여행 끝내기 카드: 마지막 카드 뒤 점선 끝에 말풍선(왼쪽) + 깃발 든 트리피(오른쪽). 누르면 부모가 한 번 더 물어봐요 -->
    <template v-if="showEnd">
      <span class="end-trail" aria-hidden="true"></span>
      <button type="button" class="trippie-end" :aria-label="isDayEnd ? '오늘 일정 끝, 내일 계속해요' : '여행 끝내기'" @click="emit('end')">
        <span v-if="isDayEnd" class="trippie-end__bubble"><span class="trippie-end__sub">오늘 일정 끝!</span><b class="trippie-end__main">내일 계속해요</b></span>
        <span v-else class="trippie-end__bubble"><span class="trippie-end__sub">여행을 마쳤으면</span><b class="trippie-end__main">나를 눌러줘!</b></span>
        <img src="/img/trippie-flag.webp" alt="" class="trippie-end__img" aria-hidden="true" />
      </button>
    </template>
    <span style="flex: none; width: 8px"></span>
  </div>
</template>

<style>
.live-cards { cursor: grab; user-select: none; }
.live-cards img { -webkit-user-drag: none; user-select: none; }
/* 일정 카드 디자인 (지도 앱 경로 카드처럼)
   - 사진을 위에 꽉 채우고, 번호는 사진 위 배지
   - 아래에 이름 한 줄 + 상태 한두 줄
   - 고른 카드: 테두리 1px + 바깥 링 1px = 2px (바탕은 안 칠해요, 크기가 안 흔들리게)
   - 공용 yh.css의 .hcard를 이 카드 줄(.live-cards)에서만 덮어써요 */
.live-cards.hscroll { gap: 4px; padding: 6px calc(50% - 78px) 10px; }
.live-cards .hcard { width: 156px; gap: 0; padding: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 16px; background: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .06); }
.live-cards .hcard img { height: 78px; border-radius: 0; }
.live-cards .hcard__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; padding: 9px 11px 11px; }
.live-cards .hcard__name { display: flex; align-items: center; gap: 4px; min-width: 0; font-size: 14px; line-height: 20px; font-weight: 700; letter-spacing: -.01em; color: var(--ink); }
.live-cards .hcard__name .ic { width: 14px; height: 14px; }
.live-cards .hcard__name-t { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.live-cards .hcard__sub { display: flex; align-items: flex-start; gap: 3px; font-size: 12px; line-height: 17px; font-weight: 500; color: var(--ink-muted); }
.live-cards .hcard__sub .ic { flex: none; width: 12px; height: 12px; margin-top: 2.5px; }
.live-cards .hcard__sub-t { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.live-cards .hcard__was { margin-right: 4px; color: var(--ink-subtle); font-weight: 500; text-decoration-thickness: 1px; } /* 원래 계획 시각 (줄 그음): 늦어진 걸 경고색 없이 */
.live-cards .hcard__no { position: absolute; left: 8px; top: 8px; z-index: 1; display: flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 6px; border-radius: 999px; background: var(--navy); color: #fff; font-size: 12px; font-weight: 700; box-shadow: 0 0 0 2px #fff; }
.live-cards .hcard__tag { left: auto; right: 8px; top: 8px; z-index: 1; height: 22px; padding: 0 8px; border-radius: 999px; }
.live-cards .hcard:hover { border-color: var(--line-strong); }
.live-cards .hcard.is-sel { padding: 0; border: 1px solid var(--navy); background: #fff; box-shadow: 0 0 0 1px var(--navy), 0 6px 16px rgba(31, 58, 104, .14); }
.live-cards .hcard.is-now { padding: 0; border: 1px solid var(--coral); box-shadow: 0 0 0 1px var(--coral), 0 6px 16px rgba(232, 96, 63, .12); }
.live-cards .hcard.is-done { background: var(--surface); box-shadow: none; }
.live-cards .hcard.is-done .hcard__name { color: var(--ink-muted); }
.live-cards .hcard.is-done.is-sel { padding: 0; border: 1px solid var(--ink-subtle); background: var(--surface); box-shadow: 0 0 0 1px var(--ink-subtle); }
.live-cards .hcard.is-done .hcard__no { background: var(--ink-subtle); }
/* 카드 사이 이동 칩: 카드와 겹치지 않게 사이 칸을 따로 둬요
   회색 점선 위에 수단 + 시간 알약, 지금 갈 구간만 남색 */
.live-cards.hscroll .segw { margin: 0; padding: 0 6px; }
.live-cards.hscroll .segw::before { height: 0; margin-top: -1px; background: none; border-top: 2px dotted var(--line-strong); }
.live-cards.hscroll .segw--now::before { border-top: 2px solid var(--navy); }
.live-cards.hscroll .segc { padding: 3px 8px; border: 1px solid var(--line); border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(24, 33, 58, .06); }
.live-cards.hscroll .segc b { gap: 3px; font-size: 11.5px; line-height: 16px; font-weight: 600; color: var(--ink); }
.live-cards.hscroll .segc:hover { border-color: var(--navy); }
/* 칩 안 작은 아래 화살표: 누르면 이동 수단·길찾기 시트가 올라온다는 표시
   마우스를 올리면 살짝 내려가요 ("눌러 봐" 느낌) */
.live-cards.hscroll .segc__more { width: 10px; height: 10px; margin-left: 1px; opacity: .55; transition: transform .15s, opacity .15s; }
.live-cards.hscroll .segc:hover .segc__more, .live-cards.hscroll .segc:focus-visible .segc__more { opacity: .9; transform: translateY(1px); }
.live-cards.hscroll .segc--now { border-color: var(--navy); background: var(--navy); }
.live-cards.hscroll .segc--now b { color: #fff; }
.live-cards.hscroll .segc--past { background: var(--surface); box-shadow: none; }
.live-cards.hscroll .segc--past b { color: var(--ink-subtle); }
/* 여행 끝내기: 테두리 없이 트리피 + 말풍선, 마지막 카드에서 점선으로 이어짐 */
.end-trail { flex: none; align-self: center; width: 44px; height: 6px; margin: 0 2px 0 6px; background: radial-gradient(circle, var(--line-strong) 1.5px, transparent 2px) 0 50% / 9px 6px repeat-x; opacity: .7; }
.trippie-end { flex: none; align-self: center; display: flex; align-items: center; gap: 8px; padding: 12px 4px 16px 8px; border: 0; border-radius: var(--radius-lg); background: none; color: var(--ink); cursor: pointer; font: inherit; text-align: left; scroll-snap-align: center; transition: transform .12s, background-color .15s; }
.trippie-end:hover .trippie-end__bubble { transform: translateY(-2px); }
.trippie-end:active { transform: scale(.96); }
.trippie-end:focus-visible { outline: 2px solid var(--navy); outline-offset: 2px; }
/* 말풍선: 흰 바탕 + 부드러운 그림자(테두리 없음), 꼬리는 둥글게 휜 모양이 트리피 쪽으로 */
.trippie-end__bubble { position: relative; display: flex; flex-direction: column; gap: 1px; padding: 12px 16px 13px; border-radius: 18px; background: #fff; filter: drop-shadow(0 6px 14px rgba(24, 33, 58, .12)) drop-shadow(0 1px 2px rgba(24, 33, 58, .08)); white-space: nowrap; transition: transform .2s ease; }
.trippie-end__bubble::after { content: ''; position: absolute; right: -11px; top: 50%; width: 14px; height: 16px; margin-top: -6px; background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 14 16'%3E%3Cpath d='M0 0Q4 8 14 10Q5 12 0 16Z' fill='%23fff'/%3E%3C/svg%3E") no-repeat center / contain; }
.trippie-end__sub { font-size: 14px; line-height: 20px; font-weight: 600; color: var(--ink); letter-spacing: -.01em; }
.trippie-end__main { font-size: 16px; line-height: 22px; font-weight: 700; color: var(--ink); letter-spacing: -.02em; }
/* 트리피 그림 (착착트립 깃발 든 전신, 확인 창에서도 같이 씀) */
.trippie-end__img { flex: none; width: 160px; height: 132px; object-fit: contain; -webkit-user-drag: none; transform-origin: 30% 100%; animation: trippieBob 2.6s ease-in-out infinite; }
@keyframes trippieBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-3px); } }
@media (prefers-reduced-motion: reduce) { .trippie-end__img { animation: none; } }
</style>
