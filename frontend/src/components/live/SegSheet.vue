<script setup>
import { toDuration } from '@/utils/time'
// 카드 사이 이동 칩을 눌렀을 때 뜨는 시트: 어디서 어디까지, 무엇으로 몇 분
// "네이버 지도로 길찾기"는 출발·도착 좌표로 네이버 지도 길찾기 웹 페이지를 새 창으로 열어요 (앱이 깔려 있으면 앱으로 넘어가요)
import { modes } from '@/data/places'
import { showToast } from '@/utils/toast'

const props = defineProps({
  from: { type: Object, required: true }, // { name, img, lat, lng }
  to: { type: Object, required: true },
  leg: { type: Object, required: true }, // { mode: walk|subway|bus|taxi, min, dist }
  current: { type: String, default: null }, // 이 칸에 저장된 수단 walk / transit / taxi (null이면 자동, 11/16)
  isSaving: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'mode']) // mode: 수단을 골랐어요 (부모가 저장하고 다시 계산)

// 이동 수단 고르기 (11/16). 값은 일정 칸 mode와 같아요 (API.md PUT /stops/{stopId}): null이면 거리로 자동(700m 안은 도보)
const MODE_OPTIONS = [
  { value: null, label: '자동', icon: 'i-route' },
  { value: 'walk', label: '도보', icon: 'i-walk' },
  { value: 'transit', label: '대중교통', icon: 'i-subway' },
  { value: 'taxi', label: '택시', icon: 'i-car' },
]

function pickMode(value) {
  if (props.isSaving || value === props.current) return
  emit('mode', value)
}

// 네이버 지도 길찾기 주소: /p/directions/{출발}/{도착}/{경유지}/{walk|transit|car}
//   출발·도착 = {경도},{위도},{이름},, (11/06: 경유지 자리 "-"가 빠져 있으면 네이버가 출발·도착 칸을 비워 둬서 넣음)
//   이름이 비면 네이버가 좌표로만 보여 줘서, 이름이 없을 땐 "출발지"·"도착지"를 넣어요
function toNaverUrl() {
  const mode = props.leg.mode === 'walk' ? 'walk' : props.leg.mode === 'taxi' ? 'car' : 'transit'
  const point = (place, fallback) => place.lng + ',' + place.lat + ',' + encodeURIComponent(place.name || fallback) + ',,'
  return 'https://map.naver.com/p/directions/' + point(props.from, '출발지') + '/' + point(props.to, '도착지') + '/-/' + mode
}

function openNaverMap() {
  window.open(toNaverUrl(), '_blank', 'noopener')
  emit('close')
  showToast('네이버 지도가 열려요 · 착착트립는 도착만 확인해요')
}
</script>

<template>
  <div class="dim" style="z-index: 7" @click="emit('close')"></div>
  <section class="sheet seg-sheet" role="dialog" aria-modal="true" style="top: 290px; z-index: 8">
    <span class="handle" aria-hidden="true"></span>
    <div class="sheet__head">
      <!-- 11/16: 위 X는 뺌 (아래 "닫기" 버튼과 겹쳐서). 바깥 어두운 곳을 눌러도 닫혀요 -->
      <h2 class="h-title" style="flex: 1; min-width: 0">{{ from.name }} <span class="muted">→</span> {{ to.name }}</h2>
    </div>
    <div class="seg-sum">
      <span class="ico-tile"><i class="ic" :class="modes[leg.mode].icon" aria-hidden="true"></i></span>
      <span>
        <b class="t-big tn">{{ toDuration(leg.min) }}</b>
        <span class="t-cap tn" style="display: block">{{ modes[leg.mode].label }} · {{ leg.dist }}</span>
      </span>
    </div>
    <!-- 이동 수단 바꾸기 (11/16): 고르면 저장하고 도착 예상을 다시 계산해요 -->
    <div class="seg-modes" role="radiogroup" aria-label="이동 수단">
      <button
        v-for="option in MODE_OPTIONS"
        :key="option.label"
        type="button"
        role="radio"
        class="seg-mode"
        :class="{ 'is-on': option.value === current }"
        :aria-checked="option.value === current"
        :disabled="isSaving"
        @click="pickMode(option.value)"
      >
        <i class="ic ic--s" :class="option.icon" aria-hidden="true"></i>{{ option.label }}
      </button>
    </div>
    <ol class="seg-ft">
      <li><span class="seg-dot"></span><img class="thumb" :src="from.img" alt="" /><span><span class="t-cap">출발</span><b class="t-strong">{{ from.name }}</b></span></li>
      <li><span class="seg-dot seg-dot--to"></span><img class="thumb" :src="to.img" alt="" /><span><span class="t-cap">도착</span><b class="t-strong">{{ to.name }}</b></span></li>
    </ol>
    <p class="t-cap" style="display: flex; gap: 6px; align-items: flex-start; margin: 0">
      <i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>길 안내는 네이버 지도가 하고, 착착트립는 도착만 확인해서 다음 일정 시간을 맞춰요
    </p>
    <div class="seg-cta">
      <button type="button" class="btn btn--primary btn--lg btn--block" @click="openNaverMap"><i class="ic i-route" aria-hidden="true"></i>네이버 지도로 길찾기</button>
      <button type="button" class="btn btn--secondary btn--lg btn--block" @click="emit('close')">닫기</button>
    </div>
  </section>
</template>

<style>
/* 이동 수단 고르기 (11/16): 네 칸 한 줄, 고른 칸은 연한 남색 + 남색 테두리 */
.seg-modes { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.seg-mode { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; height: 52px; padding: 0; border: 1px solid var(--line); border-radius: 12px; background: #fff; color: var(--ink-muted); font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; transition: background-color .15s, border-color .15s, color .15s; }
.seg-mode .ic { width: 18px; height: 18px; }
.seg-mode:hover:not(:disabled) { border-color: var(--line-strong); color: var(--ink); }
.seg-mode.is-on { border-color: var(--navy); background: var(--navy-soft); color: var(--navy); font-weight: 700; }
.seg-mode:disabled { cursor: progress; opacity: .6; }
.seg-mode:focus-visible { outline: 2px solid var(--navy); outline-offset: 2px; }
</style>
