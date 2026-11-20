<script setup>
// 일정 완성 화면 (③, 11/06): 고른 날 날씨 한 줄 + 기온별 옷차림 한 줄 (GET /api/plans/weather)
//   예) 12/5 흐림 · 5~16° · 습도 70% · 13시부터 비 / 자켓 · 트렌치코트 · 니트 · 청바지 · 우산 챙기기
//   기상청 단기예보라 3일 뒤까지만 숫자가 나와요. 그 뒤 날짜는 "3일 전부터 보여요"만, 키가 없거나 실패하면 아무것도 안 보여요
import { computed, ref, watch } from 'vue'
import { get } from '@/api/http'

const props = defineProps({
  date: { type: String, default: '' },       // 'YYYY-MM-DD' (그날)
  stops: { type: Array, default: () => [] }, // 그날 일정 칸 (좌표가 있는 첫 칸 기준으로 날씨를 받아요)
  // 한 줄로 줄이기 (11/19 ② 일정 점검·여행 상세, ③ 주형 OK): 습도·출처 줄 없이 한 줄 + [옷차림 ⌄]로 펼치기.
  //   날씨가 아직 안 나오는 날(available false: "3일 전부터 보여요" 등)은 칸을 안 보여요. false면 예전 그대로
  compact: { type: Boolean, default: false },
})

const weather = ref(null)
const isOutfitOpen = ref(false) // compact: 옷차림 줄을 펼쳤는지
const SKY_ICON = { 맑음: 'i-sun', 구름많음: 'i-cloud', 흐림: 'i-cloud', 비: 'i-rain', 눈: 'i-rain' }

// 날씨를 볼 곳: 좌표가 있는 첫 칸 (기상청 격자가 5km라 서울 안 하루 일정은 거의 같아요)
const spot = computed(() => props.stops.find((stop) => stop.lat != null && stop.lng != null) || null)
const key = computed(() => (props.date && spot.value ? `${props.date}|${spot.value.lat.toFixed(2)}|${spot.value.lng.toFixed(2)}` : ''))

watch(key, load, { immediate: true })

async function load() {
  weather.value = null
  isOutfitOpen.value = false // 다른 날로 바뀌면 옷차림은 접어서 시작
  if (!key.value) return
  const asked = key.value
  try {
    const list = await get(`/plans/weather?lat=${spot.value.lat}&lng=${spot.value.lng}&startDate=${props.date}`)
    if (asked === key.value) weather.value = list[0] || null // 날짜를 그새 바꿨으면 버림
  } catch {
    weather.value = null // 키가 없거나 기상청 오류면 줄을 숨겨요
  }
}

const monthDay = computed(() => {
  if (!props.date) return ''
  const [, month, day] = props.date.split('-')
  return Number(month) + '/' + Number(day)
})
const summary = computed(() => {
  const w = weather.value
  const parts = []
  if (w.sky) parts.push(w.sky)
  if (w.minTemp != null && w.maxTemp != null) parts.push(`${w.minTemp}~${w.maxTemp}°`)
  if (w.humidity != null) parts.push(`습도 ${w.humidity}%`)
  if (w.rainFrom) parts.push(`${Number(w.rainFrom.slice(0, 2))}시부터 ${w.sky}`)
  return parts.join(' · ')
})
// compact 한 줄: 하늘 · 최저~최고° · (비 오면) N시부터 비 (습도는 빼요)
const compactSummary = computed(() => {
  const w = weather.value
  const parts = []
  if (w.sky) parts.push(w.sky)
  if (w.minTemp != null && w.maxTemp != null) parts.push(`${w.minTemp}~${w.maxTemp}°`)
  if (w.rainFrom) parts.push(`${Number(w.rainFrom.slice(0, 2))}시부터 ${w.sky}`)
  return parts.join(' · ')
})
// 칸을 보여 줄지: 날씨를 못 받았으면 안 보여요. compact는 숫자가 없는 날(안내 글만 있는 날)도 안 보여요
const isShown = computed(() => !!weather.value && (!props.compact || weather.value.available))
</script>

<template>
  <section v-if="isShown" class="trip-weather" :class="{ 'trip-weather--compact': compact }" :aria-label="monthDay + ' 날씨와 옷차림'">
    <!-- compact: 한 줄 + 오른쪽 [옷차림 ⌄] (누르면 아래로 펼침) -->
    <template v-if="compact">
      <div class="trip-weather__row">
        <p class="trip-weather__line">
          <i class="ic ic--s" :class="SKY_ICON[weather.sky] || 'i-cloud'" aria-hidden="true"></i>
          <b class="tn">{{ monthDay }}</b><span class="tn trip-weather__text">{{ compactSummary }}</span>
        </p>
        <button v-if="weather.outfit" type="button" class="trip-weather__toggle" :aria-expanded="isOutfitOpen" @click="isOutfitOpen = !isOutfitOpen">
          옷차림<i class="ic ic--s" :class="isOutfitOpen ? 'i-up' : 'i-down'" aria-hidden="true"></i>
        </button>
      </div>
      <p v-if="isOutfitOpen && weather.outfit" class="trip-weather__outfit">
        <span class="trip-weather__label">옷차림</span>
        <span>{{ weather.outfit }}<template v-if="weather.outfitNote"><br /><small>{{ weather.outfitNote }}</small></template></span>
      </p>
    </template>
    <!-- 기본(AI 일정 만들기 화면 등): 예전 그대로 -->
    <template v-else-if="weather.available">
      <p class="trip-weather__line">
        <i class="ic ic--s" :class="SKY_ICON[weather.sky] || 'i-cloud'" aria-hidden="true"></i>
        <b class="tn">{{ monthDay }}</b><span class="tn">{{ summary }}</span>
      </p>
      <p v-if="weather.outfit" class="trip-weather__outfit">
        <span class="trip-weather__label">옷차림</span>
        <span>{{ weather.outfit }}<template v-if="weather.outfitNote"><br /><small>{{ weather.outfitNote }}</small></template></span>
      </p>
      <small class="trip-weather__source">{{ weather.source }} · 낮 평균 {{ weather.dayTemp }}° 기준</small>
    </template>
    <p v-else class="trip-weather__line trip-weather__line--muted">
      <i class="ic ic--s i-cloud" aria-hidden="true"></i>{{ weather.message }}
    </p>
  </section>
</template>

<style>
.trip-weather { display: flex; flex-direction: column; gap: 4px; margin: 0 16px 8px; padding: 10px 12px; border-radius: var(--radius-md); background: var(--surface-sunken); }
.trip-weather__line { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; line-height: 20px; color: var(--ink); }
.trip-weather__line b { font-weight: 700; }
.trip-weather__line .ic { flex: none; color: var(--navy); }
.trip-weather__line--muted { color: var(--ink-muted); }
.trip-weather__outfit { display: flex; gap: 8px; margin: 0; font-size: 13px; line-height: 20px; color: var(--ink); }
.trip-weather__outfit small { font-size: 12px; color: var(--ink-muted); }
.trip-weather__label { flex: none; font-weight: 700; color: var(--navy); }
.trip-weather__source { font-size: 10px; line-height: 14px; color: var(--ink-muted); }
/* compact (11/19): 한 줄 + 오른쪽 [옷차림 ⌄]. 글이 길면 끝을 …로 */
.trip-weather--compact { padding: 6px 6px 6px 12px; }
.trip-weather__row { display: flex; align-items: center; gap: 8px; }
.trip-weather--compact .trip-weather__line { flex: 1; min-width: 0; white-space: nowrap; }
.trip-weather__text { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.trip-weather__toggle { flex: none; display: inline-flex; align-items: center; gap: 2px; height: 28px; padding: 0 8px; border: 0; border-radius: var(--radius-sm); background: none; font: inherit; font-size: 12px; font-weight: 600; color: var(--navy); cursor: pointer; }
.trip-weather__toggle:hover { background: var(--surface-raised); }
.trip-weather--compact .trip-weather__outfit { padding: 2px 6px 4px 0; }
</style>
