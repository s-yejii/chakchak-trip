<script setup>
// 여행 중 화면 첫 안내 (코치 마크)
//   화면을 어둡게 하고 버튼 하나만 구멍처럼 밝게 비춘 뒤, 트리피가 흰 글씨로 설명해요
//   단계·문구는 utils/liveTour.js에 있어요
//   짚을 버튼에는 LiveView에서 data-tour="ask"처럼 이름표를 달아요
//   구멍 만드는 법: 버튼 자리에 투명한 칸을 놓고, 아주 큰 그림자(box-shadow 9999px)로 나머지를 어둡게 칠해요
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AppNoti from '@/components/common/AppNoti.vue'
import { TOUR_STEPS } from '@/utils/liveTour'

const emit = defineEmits(['finish'])

const HOLE_PAD = 6     // 버튼보다 구멍을 이만큼 넉넉하게 (px)
const TIP_GAP = 16     // 구멍과 설명 글 사이 (px)
const TIP_WIDTH = 260
const EDGE = 16        // 화면 가장자리 여백 (px)
const ARROW_EDGE = 22  // 화살표가 설명 글 모서리에 붙지 않게 (px)
const NOTI_IN_MS = 480  // 예시 알림이 다 내려온 뒤(yh.css notiIn 0.45초) 자리를 한 번 더 재요

const root = ref(null)
const nextButton = ref(null)
const index = ref(0)
const hole = ref(null)            // 비출 자리 { x, y, w, h } (앱 틀 기준 px). 없으면 가운데 인사 카드
const appSize = ref({ w: 390, h: 844 })

const step = computed(() => TOUR_STEPS[index.value])
const isLast = computed(() => index.value === TOUR_STEPS.length - 1)
const isAbove = computed(() => hole.value && hole.value.y + hole.value.h / 2 > appSize.value.h * 0.55) // 아래쪽 버튼이면 글은 위에

const holeStyle = computed(() => {
  const h = hole.value
  const radius = step.value.shape === 'circle' ? '50%' : step.value.shape === 'pill' ? h.h / 2 + 'px' : '22px'
  return { left: h.x + 'px', top: h.y + 'px', width: h.w + 'px', height: h.h + 'px', borderRadius: radius }
})

const tipLeft = computed(() => {
  const centerX = hole.value.x + hole.value.w / 2
  return Math.min(Math.max(centerX - TIP_WIDTH / 2, EDGE), appSize.value.w - TIP_WIDTH - EDGE)
})

const tipStyle = computed(() => {
  const style = { left: tipLeft.value + 'px', width: TIP_WIDTH + 'px' }
  if (isAbove.value) style.bottom = appSize.value.h - hole.value.y + TIP_GAP + 'px'
  else style.top = hole.value.y + hole.value.h + TIP_GAP + 'px'
  return style
})

// 화살표는 설명 글 안에서 구멍 가운데를 가리키게
const arrowLeft = computed(() => {
  const centerX = hole.value.x + hole.value.w / 2 - tipLeft.value
  return Math.min(Math.max(centerX, ARROW_EDGE), TIP_WIDTH - ARROW_EDGE) + 'px'
})

// 지금 단계 버튼의 자리를 재요
//   PC에서 휴대폰 틀이 줄어 보이면 그 비율(scale)로 나눠서 앱 틀 기준 px로 바꿔요
function measure() {
  const app = root.value && root.value.closest('.app')
  if (!app) return
  appSize.value = { w: app.offsetWidth, h: app.offsetHeight }
  const target = step.value.target && app.querySelector(`[data-tour="${step.value.target}"]`)
  if (!target) {
    hole.value = null // 짚을 게 없거나 지금 안 보이면 가운데 카드로
    return
  }
  const appBox = app.getBoundingClientRect()
  const scale = appBox.width / app.offsetWidth || 1
  const box = target.getBoundingClientRect()
  hole.value = {
    x: (box.left - appBox.left) / scale - HOLE_PAD,
    y: (box.top - appBox.top) / scale - HOLE_PAD,
    w: box.width / scale + HOLE_PAD * 2,
    h: box.height / scale + HOLE_PAD * 2,
  }
}

function next() {
  if (isLast.value) {
    emit('finish')
    return
  }
  index.value += 1
}

function skip() {
  emit('finish')
}

watch(index, async () => {
  await nextTick()
  measure()
  if (step.value.demoNoti) setTimeout(measure, NOTI_IN_MS) // 알림이 다 내려온 자리로
  nextButton.value && nextButton.value.focus()
})

onMounted(async () => {
  await nextTick()
  measure()
  nextButton.value && nextButton.value.focus()
  window.addEventListener('resize', measure)
})

onUnmounted(() => window.removeEventListener('resize', measure))
</script>

<template>
  <!-- 화면 아무 곳이나 눌러도 다음 단계로 가요 (단계마다 [다음] 버튼 자리가 달라서). [건너뛰기]는 설명 글 오른쪽 위 -->
  <div ref="root" class="tour" role="dialog" aria-modal="true" aria-label="여행 중 화면 안내" @click="next" @keydown.esc="skip" @keydown.right="next">
    <!-- 버튼을 비추는 구멍 (나머지는 그림자로 어둡게) / 짚을 게 없으면 화면 전체를 어둡게 -->
    <span v-if="hole" class="tour__hole" :style="holeStyle" aria-hidden="true"></span>
    <div v-else class="tour__dim" aria-hidden="true"></div>

    <!-- 출발 알림 단계: 진짜 앱 알림과 같은 모양의 예시 (버튼은 눌리지 않는 그림) -->
    <AppNoti v-if="step.demoNoti" data-tour="noti" class="tour__noti" warn icon="i-warning" title="지금 출발해야 해요" body="지금 나가지 않으면 저녁 예약(19:00)에 늦어요 · 여유 5분">
      <button type="button" class="btn btn--secondary btn--sm" tabindex="-1">10분 뒤 다시</button>
      <button type="button" class="btn btn--primary btn--sm" tabindex="-1">알림 끄기</button>
    </AppNoti>

    <Transition name="tour-fade" mode="out-in">
      <!-- 가운데 인사 -->
      <div v-if="!hole" :key="'hello' + index" class="tour__hello">
        <div class="tour__art" aria-hidden="true">
          <span class="tour__glow"></span>
          <img src="/img/trippie-flag.webp" alt="" draggable="false" />
        </div>
        <p class="tour__title">{{ step.title }}</p>
        <p class="tour__text">{{ step.text }}</p>
        <button ref="nextButton" type="button" class="tour__next tour__next--wide" @click.stop="next">{{ isLast ? '시작하기' : '알려 줘' }}</button>
        <button type="button" class="tour__skip" @click.stop="skip">건너뛰기</button>
      </div>

      <!-- 버튼 옆 설명: 흰 글씨 + 버튼을 가리키는 화살표 -->
      <div v-else :key="'tip' + index" class="tour__tip" :class="{ 'is-above': isAbove }" :style="tipStyle" aria-live="polite">
        <span class="tour__arrow" :style="{ left: arrowLeft }" aria-hidden="true"></span>
        <p class="tour__who">
          <img src="/img/trippie-favicon.png" alt="" class="tour__who-img" draggable="false" />트리피
          <span class="tour__count tn">{{ index }} / {{ TOUR_STEPS.length - 1 }}</span>
          <button v-if="!isLast" type="button" class="tour__skip tour__skip--inline" @click.stop="skip">건너뛰기</button>
        </p>
        <p class="tour__title">{{ step.title }}</p>
        <p class="tour__text">{{ step.text }}</p>
        <div class="tour__foot">
          <span class="tour__dots" aria-hidden="true">
            <span v-for="(s, i) in TOUR_STEPS.slice(1)" :key="i" class="tour__dot" :class="{ 'is-on': i + 1 === index }"></span>
          </span>
          <button ref="nextButton" type="button" class="tour__next" @click.stop="next">{{ isLast ? '시작하기' : '다음' }}</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style>
/* 화면 전체를 덮어서 안내 중에는 뒤 버튼이 눌리지 않게 */
.tour { position: absolute; inset: 0; z-index: 60; color: #fff; cursor: pointer; } /* 진짜 알림 줄(.noti z 55)보다 위 */
.tour__dim { position: absolute; inset: 0; background: rgba(8, 17, 36, .76); }
/* 구멍: 투명한 칸 + 바깥으로 아주 큰 그림자 = 나머지만 어둡게. 흰 테두리 한 줄로 또렷하게 */
.tour__hole { position: absolute; box-shadow: 0 0 0 2px rgba(255, 255, 255, .92), 0 0 0 9999px rgba(8, 17, 36, .76); pointer-events: none; transition: left .35s cubic-bezier(.3, .7, .3, 1), top .35s cubic-bezier(.3, .7, .3, 1), width .35s cubic-bezier(.3, .7, .3, 1), height .35s cubic-bezier(.3, .7, .3, 1), border-radius .35s; }
/* 구멍 둘레가 천천히 퍼지는 빛 (여기를 보라는 신호) */
.tour__hole::after { content: ''; position: absolute; inset: -2px; border-radius: inherit; box-shadow: 0 0 0 0 rgba(255, 255, 255, .55); animation: tourPulse 1.8s ease-out infinite; }
@keyframes tourPulse { 0% { box-shadow: 0 0 0 0 rgba(255, 255, 255, .5); } 100% { box-shadow: 0 0 0 12px rgba(255, 255, 255, 0); } }

/* 버튼 옆 설명 */
.tour__tip { position: absolute; }
.tour__arrow { position: absolute; top: -9px; width: 0; height: 0; margin-left: -9px; border-left: 9px solid transparent; border-right: 9px solid transparent; border-bottom: 9px solid #fff; }
.tour__tip.is-above .tour__arrow { top: auto; bottom: -9px; border-bottom: 0; border-top: 9px solid #fff; }
.tour__who { display: flex; align-items: center; gap: 6px; margin: 2px 0 4px; font-size: 12px; line-height: 16px; font-weight: 600; color: rgba(255, 255, 255, .7); }
.tour__who-img { width: 20px; height: 20px; border-radius: 50%; background: var(--sky-soft); object-fit: cover; }
.tour__count { margin-left: auto; color: rgba(255, 255, 255, .6); }
.tour__count + .tour__skip--inline::before { content: '·'; margin-right: 6px; color: rgba(255, 255, 255, .4); }
/* 글씨: 제목 20px 굵게 + 설명 15px 한 줄 (말은 짧게, 크기는 또렷하게) */
.tour__title { margin: 0 0 2px; font-size: 20px; line-height: 28px; font-weight: 700; letter-spacing: -0.2px; color: #fff; }
.tour__text { margin: 0; font-size: 15px; line-height: 22px; color: rgba(255, 255, 255, .86); word-break: keep-all; }
.tour__foot { display: flex; align-items: center; gap: 4px; margin-top: 12px; }
.tour__dots { flex: 1; display: flex; align-items: center; gap: 5px; }
.tour__dot { width: 6px; height: 6px; border-radius: 999px; background: rgba(255, 255, 255, .32); transition: width .25s, background .25s; }
.tour__dot.is-on { width: 18px; background: #fff; }

/* 버튼 */
.tour__next { height: 36px; padding: 0 16px; border: 0; border-radius: 999px; background: #fff; color: var(--navy); font: inherit; font-size: 14px; font-weight: 700; cursor: pointer; box-shadow: 0 4px 14px rgba(0, 0, 0, .18); }
.tour__next:hover { background: var(--sky-soft); }
.tour__next--wide { width: 200px; height: 48px; font-size: 16px; }
.tour__skip { height: 36px; padding: 0 10px; border: 0; background: none; color: rgba(255, 255, 255, .72); font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.tour__skip:hover { color: #fff; }
.tour__skip--inline { height: 24px; margin-right: -6px; padding: 0 6px; font-size: 12.5px; }
.tour__noti { pointer-events: none; } /* 예시 알림: 밀거나 눌리지 않게 (누르면 안내가 다음으로) */
.tour__next:focus-visible, .tour__skip:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }

/* 가운데 인사 */
.tour__hello { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; padding: 0 32px; text-align: center; }
.tour__hello .tour__title { font-size: 26px; line-height: 34px; }
.tour__hello .tour__text { font-size: 16px; line-height: 24px; }
.tour__hello .tour__next { margin-top: 24px; }
.tour__hello .tour__skip { height: 44px; margin-top: 4px; padding: 0 16px; font-size: 15px; color: rgba(255, 255, 255, .82); text-decoration: underline; text-underline-offset: 4px; text-decoration-color: rgba(255, 255, 255, .4); }
.tour__art { position: relative; width: 170px; height: 150px; margin-bottom: 6px; }
.tour__art img { position: relative; width: 100%; height: 100%; object-fit: contain; -webkit-user-drag: none; animation: tourBob 2.6s ease-in-out infinite; }
.tour__glow { position: absolute; inset: 10px 0 0; border-radius: 50%; background: radial-gradient(circle, rgba(181, 221, 245, .45) 0%, rgba(181, 221, 245, 0) 70%); }
@keyframes tourBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }

/* 단계 바뀔 때 글이 살짝 떠오르며 바뀜 */
.tour-fade-enter-active, .tour-fade-leave-active { transition: opacity .22s, transform .22s; }
.tour-fade-enter-from { opacity: 0; transform: translateY(6px); }
.tour-fade-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .tour__hole, .tour__dot, .tour-fade-enter-active, .tour-fade-leave-active { transition: none; }
  .tour__hole::after, .tour__art img { animation: none; }
}
</style>
