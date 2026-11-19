<script setup>
// ⑤ 웹 회고 쇼츠 (11/19 피드백: "웹에도 쇼츠, 웹 화면에 맞게")
// 앱 쇼츠(ShortsView)와 같은 방식: 첫 장(제목) → 사진·영상 한 장씩 자동으로 → 끝 장(사진 모아 보기)
// 다른 점: 이 여행 하나만, 휴대폰 세로 칸 대신 PC 가로 화면(16:9) 가득, 키보드(← → 스페이스 Esc)로도 넘김
// 사진은 부모가 이미 불러온 것(GET /api/trips/{id}/photos)을 받고, AI 자막만 GET /api/trips/{id}/shorts로 불러옴
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { get } from '@/api/http'

const props = defineProps({
  tripId: { type: [String, Number], required: true },
  retro: { type: Object, required: true },   // 제목·날짜·인원·장소 이름
  photos: { type: Array, required: true },   // 이 여행 사진·영상
})
const emit = defineEmits(['close'])

const FIRST_MS = 3000        // 첫 장(제목)을 보여 주는 시간
const PHOTO_MS = 4000        // 사진 한 장을 보여 주는 시간
const VIDEO_MAX_MS = 15000   // 영상이 길어도 이만큼만 보고 넘어감
const LAST_MS = 6000         // 끝 장을 보여 주는 시간
const TICK_MS = 100          // 진행 막대를 다시 그리는 간격
const COLLAGE_MAX = 5        // 끝 장에 늘어놓을 사진 수

const slideIndex = ref(0)
const progress = ref(0)      // 지금 장이 얼마나 지났는지 0 ~ 1
const isPaused = ref(false)
const isMuted = ref(false)
const isEnded = ref(false)   // 끝 장까지 다 봤는지 (다시 보기 버튼)
const captions = ref({})     // AI 감성 자막: 사진 번호 → 자막
const videoEl = ref(null)
let timer = null
let elapsed = 0

// 장 목록: 첫 장 + 사진마다 한 장 + 끝 장. 예: 사진 12장 → 14장
const slides = computed(() => {
  const list = [{ type: 'first' }]
  for (const photo of props.photos) list.push({ type: 'photo', photo: photo })
  list.push({ type: 'last' })
  return list
})

const slide = computed(() => slides.value[slideIndex.value])

// 첫 장 배경: 영상이 아닌 첫 사진
const coverPhoto = computed(() => {
  for (const photo of props.photos) {
    if (photo.mediaType !== 'video') return photo
  }
  return null
})

// 끝 장에 늘어놓을 사진: 처음·가운데·끝이 고루 나오게 띄엄띄엄 고름. 예: 20장 → 0, 4, 8, 12, 16번째
const collage = computed(() => {
  const list = []
  const step = Math.max(1, Math.floor(props.photos.length / COLLAGE_MAX))
  for (let i = 0; i < props.photos.length && list.length < COLLAGE_MAX; i += step) list.push(props.photos[i])
  return list
})

onMounted(() => {
  window.addEventListener('keydown', onKey)
  loadCaptions()
  startSlide()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  stopTimer()
})

// AI 자막은 몇 초 걸릴 수 있어서 기다리지 않고, 오는 대로 붙임 (실패하면 자막 없이)
async function loadCaptions() {
  try {
    const shorts = await get('/trips/' + props.tripId + '/shorts')
    for (const item of shorts.captions) captions.value[item.photoId] = item.caption
  } catch {
    // 자막 없이 보여 줌
  }
}

function startSlide() {
  stopTimer()
  elapsed = 0
  progress.value = 0
  isPaused.value = false
  timer = setInterval(tick, TICK_MS)
}

function stopTimer() {
  clearInterval(timer)
  timer = null
}

function tick() {
  if (isPaused.value) return
  elapsed = elapsed + TICK_MS
  progress.value = Math.min(1, elapsed / slideDuration(slide.value))
  if (progress.value >= 1) goNext()
}

// 장마다 보여 주는 시간(ms). 영상은 길이만큼, 너무 길면 VIDEO_MAX_MS까지
function slideDuration(item) {
  if (item.type === 'first') return FIRST_MS
  if (item.type === 'last') return LAST_MS
  if (item.photo.mediaType === 'video' && item.photo.durationSec) return Math.min(VIDEO_MAX_MS, item.photo.durationSec * 1000)
  return PHOTO_MS
}

function goNext() {
  if (slideIndex.value < slides.value.length - 1) {
    slideIndex.value++
    startSlide()
    return
  }
  stopTimer() // 끝 장에서 멈추고 [처음부터] 버튼을 보여 줌
  progress.value = 1
  isEnded.value = true
}

function goPrev() {
  if (slideIndex.value === 0) return
  slideIndex.value--
  isEnded.value = false
  startSlide()
}

function goTo(index) {
  slideIndex.value = index
  isEnded.value = false
  startSlide()
}

function togglePause() {
  isPaused.value = !isPaused.value
  if (videoEl.value === null) return
  if (isPaused.value) videoEl.value.pause()
  else videoEl.value.play()
}

function close() {
  stopTimer()
  emit('close')
}

// 키보드: ← 이전, → 다음, 스페이스 멈춤, Esc 닫기
function onKey(event) {
  if (event.key === 'ArrowRight') goNext()
  else if (event.key === 'ArrowLeft') goPrev()
  else if (event.key === 'Escape') close()
  else if (event.key === ' ') togglePause()
  else return
  event.preventDefault()
}

// 화면을 누르면 왼쪽 1/3은 이전 장, 나머지는 다음 장
function onStageClick(event) {
  const box = event.currentTarget.getBoundingClientRect()
  if (event.clientX - box.left < box.width / 3) goPrev()
  else goNext()
}

// 사진을 찍은 장소 이름. 이동 중에 찍었으면 "1일째 13:30"
function placeName(photo) {
  for (const stop of props.retro.stops) {
    if (stop.id === photo.stopId) return stop.name
  }
  return photo.day + '일째 ' + photo.takenAt
}

// "2026-11-16" ~ "2026-11-17" → "2026.11.16 ~ 11.17"
function dateText() {
  const start = props.retro.startDate.replaceAll('-', '.')
  if (props.retro.startDate === props.retro.endDate) return start
  return start + ' ~ ' + props.retro.endDate.slice(5).replace('-', '.')
}
</script>

<template>
  <div class="rs" role="dialog" aria-modal="true" aria-label="여행 쇼츠">
    <div class="rs-stage" @click="onStageClick">
      <!-- 위: 진행 막대 (칸을 누르면 그 장으로) -->
      <div class="rs-segs" @click.stop>
        <button v-for="(item, i) in slides" :key="i" type="button" class="rs-seg" :aria-label="(i + 1) + '번째 장으로'" @click="goTo(i)">
          <span><i :style="{ width: (i < slideIndex ? 100 : i === slideIndex ? progress * 100 : 0) + '%' }"></i></span>
        </button>
      </div>

      <!-- 첫 장: 대표 사진 + 제목 -->
      <template v-if="slide.type === 'first'">
        <template v-if="coverPhoto">
          <img :src="coverPhoto.url" alt="" class="rs-blur" />
          <img :src="coverPhoto.url" alt="" class="rs-media rs-zoom" />
        </template>
        <div class="rs-shade"></div>
        <div class="rs-text rs-text--first">
          <p class="rs-kicker">{{ dateText() }} · {{ retro.people }}명</p>
          <h1>{{ retro.title }}</h1>
          <p class="rs-sub">사진·영상 {{ photos.length }}장 · ← → 키로 넘겨요</p>
        </div>
      </template>

      <!-- 사진 한 장씩: 가로 화면 가득 (잘리지 않게, 남는 칸은 같은 사진을 흐리게) -->
      <template v-else-if="slide.type === 'photo'">
        <img v-if="slide.photo.mediaType !== 'video'" :key="'b' + slide.photo.id" :src="slide.photo.url" alt="" class="rs-blur" />
        <video v-if="slide.photo.mediaType === 'video'" ref="videoEl" :key="'v' + slide.photo.id" :src="slide.photo.url" :muted="isMuted" class="rs-media" autoplay playsinline @ended="goNext"></video>
        <img v-else :key="'p' + slide.photo.id" :src="slide.photo.url" alt="" class="rs-media rs-zoom" />
        <div class="rs-shade"></div>
        <div class="rs-text">
          <p v-if="captions[slide.photo.id]" :key="'c' + slide.photo.id" class="rs-line">{{ captions[slide.photo.id] }}</p>
          <span class="rs-chip">📍 {{ placeName(slide.photo) }}</span>
          <span class="rs-sub">{{ slide.photo.day }}일째 {{ slide.photo.takenAt }}</span>
        </div>
      </template>

      <!-- 끝 장: 사진을 한 줄로 늘어놓기 -->
      <template v-else>
        <div class="rs-end">
          <h1>이번 여행, 여기까지예요</h1>
          <p class="rs-sub">{{ retro.title }} · 사진·영상 {{ photos.length }}장</p>
          <div class="rs-collage">
            <div v-for="(photo, i) in collage" :key="photo.id" class="rs-polaroid" :style="{ '--rot': (i % 2 === 0 ? -4 : 4) + 'deg', 'animation-delay': i * 0.12 + 's' }">
              <video v-if="photo.mediaType === 'video'" :src="photo.url" muted preload="metadata"></video>
              <img v-else :src="photo.url" alt="" />
              <p>{{ placeName(photo) }}</p>
            </div>
          </div>
          <button v-if="isEnded" type="button" class="rs-pill" @click.stop="goTo(0)">↻ 처음부터 다시 보기</button>
        </div>
      </template>

      <!-- 오른쪽 아래: 멈춤·소리·닫기 -->
      <div class="rs-controls" @click.stop>
        <button type="button" class="rs-icon" :aria-label="isPaused ? '재생' : '멈춤'" @click="togglePause">{{ isPaused ? '▶' : '❚❚' }}</button>
        <button type="button" class="rs-icon" :aria-label="isMuted ? '소리 켜기' : '소리 끄기'" @click="isMuted = !isMuted">{{ isMuted ? '🔇' : '🔊' }}</button>
      </div>
    </div>
    <button type="button" class="rs-close" aria-label="쇼츠 닫기" @click="close">✕</button>
  </div>
</template>

<style scoped>
/* 화면 전체를 어둡게 덮고, 가운데에 16:9 무대 (창 크기에 맞춰 가로·세로 중 꽉 차는 쪽으로) */
.rs { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; background: rgba(6, 9, 20, 0.92); }
.rs-stage { position: relative; width: min(94vw, calc((100vh - 64px) * 16 / 9)); aspect-ratio: 16 / 9; overflow: hidden; border-radius: 16px; background: #000; color: #fff; cursor: pointer; user-select: none; }
.rs-blur { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; transform: scale(1.15); filter: blur(28px) brightness(0.5); }
.rs-media { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
.rs-zoom { animation: rs-zoom 6s ease-out forwards; }
@keyframes rs-zoom { from { transform: scale(1); } to { transform: scale(1.05); } }
.rs-shade { position: absolute; inset: 0; pointer-events: none; background: linear-gradient(to bottom, rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0) 18%, rgba(0, 0, 0, 0) 60%, rgba(0, 0, 0, 0.7)); }

.rs-segs { position: absolute; top: 10px; left: 16px; right: 16px; z-index: 2; display: flex; gap: 4px; }
.rs-seg { flex: 1; min-width: 0; padding: 8px 0; border: 0; background: transparent; cursor: pointer; }
.rs-seg span { display: block; height: 3px; overflow: hidden; border-radius: 999px; background: rgba(255, 255, 255, 0.35); }
.rs-seg i { display: block; height: 3px; background: #fff; }

.rs-text { position: absolute; left: 40px; right: 200px; bottom: 36px; z-index: 2; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45); }
.rs-text h1 { margin: 0; font-size: 44px; line-height: 54px; letter-spacing: -0.02em; }
.rs-kicker { margin: 0; color: #B5DDF5; font-size: 16px; font-weight: 600; }
.rs-sub { margin: 0; color: rgba(255, 255, 255, 0.8); font-size: 15px; }
.rs-line { margin: 0; font-size: 28px; line-height: 38px; font-weight: 700; animation: rs-fade 0.6s ease-out both; }
@keyframes rs-fade { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
.rs-chip { padding: 4px 12px; border-radius: 999px; background: rgba(0, 0, 0, 0.45); font-size: 15px; font-weight: 600; }

.rs-end { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: #0A0E1C; text-align: center; }
.rs-end h1 { margin: 0; font-size: 36px; }
.rs-collage { display: flex; justify-content: center; gap: 18px; margin: 24px 0 12px; }
.rs-polaroid { width: 180px; padding: 6px 6px 30px; position: relative; border-radius: 10px; background: #fff; box-shadow: 0 4px 18px rgba(0, 0, 0, 0.4); transform: rotate(var(--rot)); animation: rs-drop 0.7s cubic-bezier(0.2, 0.8, 0.2, 1) both; }
.rs-polaroid img, .rs-polaroid video { display: block; width: 100%; height: 200px; object-fit: cover; border-radius: 6px; }
.rs-polaroid p { position: absolute; left: 8px; right: 8px; bottom: 7px; margin: 0; overflow: hidden; color: #18213A; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
@keyframes rs-drop { from { opacity: 0; transform: translateY(-30px) rotate(0deg) scale(0.8); } to { opacity: 1; transform: rotate(var(--rot)); } }
.rs-pill { padding: 10px 18px; border: 0; border-radius: 999px; background: rgba(255, 255, 255, 0.16); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; }

.rs-controls { position: absolute; right: 20px; bottom: 24px; z-index: 3; display: flex; gap: 8px; }
.rs-icon { width: 44px; height: 44px; border: 0; border-radius: 50%; background: rgba(0, 0, 0, 0.45); color: #fff; font-size: 16px; cursor: pointer; }
.rs-close { position: absolute; top: 16px; right: 20px; width: 44px; height: 44px; border: 0; border-radius: 50%; background: rgba(255, 255, 255, 0.14); color: #fff; font-size: 18px; cursor: pointer; }
</style>
