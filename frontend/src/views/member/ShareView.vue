<script setup>
// 공유 일정 보기 (①, 11/16 상재): 카카오톡으로 받은 링크 /s/{code}를 로그인 없이 열어 일정만 봐요.
//   /live처럼 위에 지도(번호 핀 + 동선), 아래에 일차 버튼과 장소 카드. 길 안내·일정 변경·AI 묻기 같은 기능은 없어요.
//   카드에 마우스를 올리거나 누르면 지도가 그 장소로 움직이고, 핀을 누르면 그 카드로 목록이 내려가요.
//   로그인이 필요한 API는 부르지 않아요 (사진도 공유 응답에 같이 들어 있음)
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/api/http'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import KakaoMap from '@/components/common/KakaoMap.vue'

const MAP_PADDING = { top: 40, right: 40, bottom: 40, left: 40 } // 지도는 화면 위쪽 칸에만 있어서 가리는 곳이 없음
const NAVER_PLACE_URL = 'https://map.naver.com/p/entry/place/'
const NAVER_SEARCH_URL = 'https://map.naver.com/p/search/'

const route = useRoute()
const trip = ref(null)
const day = ref(1)
const isLoading = ref(true)
const errorMessage = ref('')
const isMapOk = ref(true)
const selected = ref(-1) // 강조한 카드 번호 (0부터, 없으면 -1)
const mapRef = ref(null)
const listRef = ref(null)

onMounted(async () => {
  try {
    trip.value = await get('/share/' + encodeURIComponent(route.params.code))
    document.title = trip.value.title + ' · 착착트립'
    if (trip.value.days.length) day.value = trip.value.days[0].day
  } catch (e) {
    errorMessage.value = e.status === 404 ? '공유가 끝났거나 없는 일정이에요' : '일정을 불러오지 못했어요 (' + e.message + ')'
  }
  isLoading.value = false
})

// 고른 날의 장소들
const stops = computed(() => {
  if (!trip.value) return []
  const found = trip.value.days.find((item) => item.day === day.value)
  return found ? found.stops : []
})

// 지도 핀: 번호 순서대로 동선을 이어요 (좌표 없는 칸은 지도에서만 뺌). 강조한 장소는 크게
const mapPins = computed(() => {
  const pins = []
  stops.value.forEach((stop, index) => {
    if (stop.lat == null || stop.lng == null) return
    const no = index + 1
    const cls = index === selected.value ? ' pin--sel' : ''
    pins.push({ key: day.value + '-' + index, index, lat: stop.lat, lng: stop.lng, text: no, label: no + ' ' + stop.name, cls })
  })
  return pins
})

// 날을 바꾸면 강조를 지우고 목록을 맨 위로 (지도는 fit-key로 그 날 장소가 다 보이게 다시 맞춤)
watch(day, () => {
  selected.value = -1
  if (listRef.value) listRef.value.scrollTop = 0
})

// 카드에 마우스를 올리거나 누름 → 그 장소를 강조하고 지도 가운데로 부드럽게 옮김
function focusStop(index) {
  if (selected.value === index) return
  selected.value = index
  const stop = stops.value[index]
  if (mapRef.value && stop.lat != null && stop.lng != null) mapRef.value.flyTo(stop.lat, stop.lng)
}

// 지도 핀을 누름 → 그 카드를 강조하고 목록을 그 카드까지 스크롤
async function pickPin(pin) {
  focusStop(pin.index)
  await nextTick()
  const card = document.getElementById('share-stop-' + pin.index)
  if (card) card.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// "11/16(월)" 모양 날짜
function dateText(text) {
  if (!text) return ''
  const date = new Date(text + 'T00:00:00')
  return (date.getMonth() + 1) + '/' + date.getDate() + '(' + '일월화수목금토'[date.getDay()] + ')'
}
const period = computed(() => {
  if (!trip.value) return ''
  const start = dateText(trip.value.startDate)
  return trip.value.endDate && trip.value.endDate !== trip.value.startDate ? start + ' ~ ' + dateText(trip.value.endDate) : start
})

// 그 날의 날짜 (1일차 = 시작일)
function dayDate(dayNumber) {
  if (!trip.value) return ''
  const date = new Date(trip.value.startDate + 'T00:00:00')
  date.setDate(date.getDate() + dayNumber - 1)
  return (date.getMonth() + 1) + '/' + date.getDate()
}

function photoOf(stop) {
  return stop.imageUrl || imageFor(stop.category)
}

// [네이버 지도] 버튼: 네이버 장소면 그 장소, 아니면 이름으로 검색. 새 탭으로
function openNaver(stop) {
  const match = /^naver-(\d+)$/.exec(stop.placeId || '')
  const url = match ? NAVER_PLACE_URL + match[1] : NAVER_SEARCH_URL + encodeURIComponent(stop.name)
  window.open(url, '_blank', 'noopener')
}
</script>

<template>
  <main class="share">
    <p v-if="isLoading" class="share__msg">일정을 불러오는 중이에요…</p>
    <p v-else-if="errorMessage" class="share__msg">{{ errorMessage }}</p>
    <template v-else>
      <div class="share__map">
        <KakaoMap v-if="isMapOk && mapPins.length" ref="mapRef" :pins="mapPins" :padding="MAP_PADDING" :fit-key="day" @pick="pickPin" @fail="isMapOk = false" />
      </div>

      <section ref="listRef" class="share__body">
        <h1 class="share__title">{{ trip.title }}</h1>
        <p class="share__sub">{{ period }} · 착착트립으로 만든 일정</p>

        <div v-if="trip.days.length > 1" class="share__days" role="tablist">
          <button v-for="item in trip.days" :key="item.day" type="button" role="tab" :aria-selected="item.day === day"
            :class="['share__day', { 'share__day--on': item.day === day }]" @click="day = item.day">
            {{ item.day }}일차 <span>{{ dayDate(item.day) }}</span>
          </button>
        </div>

        <p v-if="!stops.length" class="share__msg">이 날은 일정이 없어요</p>
        <ol class="share__list">
          <li v-for="(stop, index) in stops" :id="'share-stop-' + index" :key="day + '-' + index"
            :class="['share__card', { 'share__card--on': index === selected }]"
            tabindex="0" @mouseenter="focusStop(index)" @click="focusStop(index)" @focus="focusStop(index)">
            <span class="share__time">{{ stop.time }}</span>
            <img class="share__photo" :src="photoOf(stop)" alt="" loading="lazy" @error="$event.target.src = imageFor(stop.category)" />
            <span class="share__info">
              <span class="share__name"><b>{{ index + 1 }}</b> {{ stop.name }}</span>
              <span class="share__meta">{{ stop.category }}<template v-if="stop.stay"> · {{ toDuration(stop.stay) }}</template></span>
            </span>
            <button type="button" class="share__naver" :aria-label="stop.name + ' 네이버 지도에서 보기'" @click.stop="openNaver(stop)">네이버 지도</button>
          </li>
        </ol>
        <p class="share__foot">장소에 마우스를 올리거나 누르면 지도가 그 장소로 움직여요</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
/* 앱 전체가 body 스크롤을 막아 둬서(app.css), 이 화면은 지도 아래 목록 칸이 따로 스크롤돼요 */
.share { height: 100vh; height: 100dvh; max-width: 560px; margin: 0 auto; display: flex; flex-direction: column; background: var(--surface, #fff); color: var(--ink, #1b2333); }
.share__map { position: relative; flex: none; height: 42%; min-height: 220px; background: var(--surface-sunken, #eef1f6); }
.share__body { flex: 1; min-height: 0; overflow-y: auto; padding: 20px 16px 32px; }
.share__title { margin: 0; font-size: 22px; font-weight: 800; }
.share__sub { margin: 4px 0 16px; font-size: 13px; color: var(--ink-muted, #667085); }
.share__days { display: flex; gap: 8px; margin-bottom: 16px; overflow-x: auto; }
.share__day { flex: none; padding: 8px 14px; border: 1px solid var(--line-strong, #c9d0dc); border-radius: 999px; background: var(--surface, #fff); color: var(--ink, #1b2333); font-weight: 700; font-size: 14px; cursor: pointer; }
.share__day span { font-weight: 400; margin-left: 4px; }
.share__day--on { background: var(--navy, #1f3a68); border-color: var(--navy, #1f3a68); color: #fff; }
.share__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.share__card { display: flex; align-items: center; gap: 12px; padding: 10px; border: 1px solid var(--line, #e3e7ee); border-radius: 14px; background: var(--surface-raised, #fff); cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .15s; outline: none; }
.share__card--on { border-color: var(--navy, #1f3a68); box-shadow: 0 0 0 2px var(--navy, #1f3a68); transform: translateY(-1px); }
.share__time { flex: none; width: 44px; font-weight: 800; font-size: 15px; }
.share__photo { flex: none; width: 56px; height: 56px; border-radius: 10px; object-fit: cover; background: var(--surface-sunken, #eef1f6); }
.share__info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.share__name { font-weight: 700; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share__name b { display: inline-grid; place-items: center; width: 20px; height: 20px; margin-right: 4px; border-radius: 50%; background: var(--navy, #1f3a68); color: #fff; font-size: 12px; }
.share__meta { font-size: 13px; color: var(--ink-muted, #667085); }
.share__naver { flex: none; padding: 6px 10px; border: 1px solid var(--line-strong, #c9d0dc); border-radius: 8px; background: var(--surface, #fff); color: var(--ink, #1b2333); font-size: 12px; font-weight: 700; cursor: pointer; }
.share__naver:hover { border-color: #03c75a; color: #03a14a; }
.share__msg { padding: 40px 16px; text-align: center; color: var(--ink-muted, #667085); }
.share__foot { margin-top: 16px; font-size: 12px; text-align: center; color: var(--ink-subtle, #98a2b3); }
</style>
