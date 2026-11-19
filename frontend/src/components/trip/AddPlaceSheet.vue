<script setup>
// 일정 편집 [장소 추가] → 아래에서 올라오는 "장소 넣기" 시트 (②, 11/19)
// 위: 장소 이름 검색, 아래: 종류 칩. 줄마다 이름 · 종류 · 기준 위치에서 걸어서 몇 분 + [넣기]
// 찾기는 직접 고르기 화면(PlanManualView)과 같은 API: 칩 GET /api/plans/places, 검색 GET /api/places/search
// 어디에 넣을지는 편집 화면이 정해요 (이 시트는 고른 장소만 'add'로 알려 줌)
import { onUnmounted, ref, watch } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import ChipScroller from '@/components/common/ChipScroller.vue'
import { getManualPlaces, searchPlaces } from '@/api/trips'
import { distanceMeters } from '@/utils/geo'

const KINDS = [
  { code: 'food', label: '맛집' },
  { code: 'cafe', label: '카페' },
  { code: 'bar', label: '술집' },
  { code: 'culture', label: '볼거리' },
  { code: 'tour', label: '들를 곳' },
]
const RESULT_MAX = 20 // 목록에 보여 줄 장소 수
const SEARCH_DELAY_MS = 300 // 글자를 다 친 뒤 이만큼 기다렸다 검색 (칠 때마다 서버에 안 가게)
const WALK_M_PER_MIN = 67 // 걷는 속도 4km/h (직선거리로 대충 잼)

const props = defineProps({
  base: { type: Object, required: true }, // 찾는 기준 위치 { lat, lng, name }
  dayStops: { type: Array, required: true }, // 그날 칸 (이미 있는 곳은 [넣음])
})
const emit = defineEmits(['add', 'close'])

const keyword = ref('')
const kind = ref('food')
const results = ref([])
const isLoading = ref(false)
const message = ref('')
let searchTimer = null
let lastRequest = 0 // 빨리 여러 번 찾으면 마지막 결과만 보여 줘요

loadKind()
onUnmounted(() => clearTimeout(searchTimer))

// 칩으로 찾기: 기준 위치 근처 그 종류 장소
function loadKind() {
  return find(() => getManualPlaces(props.base.lat, props.base.lng, kind.value), '근처에 이 종류 장소가 없어요')
}
function pickKind(code) {
  keyword.value = ''
  kind.value = code
  loadKind()
}
// 이름으로 찾기 (기준 위치에서 가까운 순)
function runSearch(word) {
  return find(() => searchPlaces(word, props.base.lat, props.base.lng), '찾는 곳이 없어요')
}
watch(keyword, (word) => {
  clearTimeout(searchTimer)
  if (!word.trim()) return loadKind()
  searchTimer = setTimeout(() => runSearch(word.trim()), SEARCH_DELAY_MS)
})

// 찾기 공통: 불러오는 중 표시, 실패하면 안내 글, 늦게 온 옛 결과는 버림
async function find(request, emptyText) {
  const myRequest = ++lastRequest
  isLoading.value = true
  message.value = ''
  try {
    const list = await request()
    if (myRequest !== lastRequest) return
    results.value = list.slice(0, RESULT_MAX)
    if (!results.value.length) message.value = emptyText
  } catch (e) {
    if (myRequest !== lastRequest) return
    results.value = []
    message.value = e.message
  }
  isLoading.value = false
}

// 기준 위치에서 걸어서 몇 분 (직선거리 ÷ 걷는 속도, 최소 1분)
function walkMinutes(place) {
  const meters = distanceMeters(props.base.lat, props.base.lng, place.lat, place.lng)
  return Math.max(1, Math.round(meters / WALK_M_PER_MIN))
}
// 그날 이미 있는 곳인지 (장소 id가 같거나 이름이 같으면)
function isAdded(place) {
  return props.dayStops.some((stop) => (place.id && stop.placeId === place.id) || stop.name === place.name)
}
</script>

<template>
  <div class="dim" style="z-index: 40"></div>
  <BottomSheet closable :on-close="() => emit('close')" class="aps-sheet" role="dialog" aria-modal="true" aria-labelledby="aps-title">
    <div class="aps-head">
      <h2 id="aps-title" class="h-title">장소 넣기</h2>
      <button type="button" class="iconbtn" aria-label="닫기" @click="emit('close')"><i class="ic i-close" aria-hidden="true"></i></button>
    </div>
    <p class="t-cap aps-base">{{ base.name }} 기준</p>

    <div class="field">
      <label for="aps-q" class="sr">장소 검색</label>
      <div class="field__box"><i class="ic i-search subtle" aria-hidden="true"></i><input id="aps-q" v-model="keyword" type="search" placeholder="장소 이름 검색" autocomplete="off" /></div>
    </div>
    <ChipScroller role="group" aria-label="장소 종류">
      <button v-for="item in KINDS" :key="item.code" type="button" class="chip chip--sm" :class="{ 'is-on': !keyword && kind === item.code }" :aria-pressed="!keyword && kind === item.code" @click="pickKind(item.code)">{{ item.label }}</button>
    </ChipScroller>

    <div class="aps-list">
      <p v-if="isLoading" class="t-cap">찾는 중이에요</p>
      <p v-else-if="message" class="t-cap">{{ message }}</p>
      <div v-else class="group">
        <div v-for="place in results" :key="place.id || place.name" class="row">
          <span class="row__body">
            <span class="row__title">{{ place.name }}</span>
            <span class="row__meta tn">{{ (place.category || '').split(',')[0] }} · 걸어서 {{ walkMinutes(place) }}분</span>
          </span>
          <button type="button" class="btn btn--secondary btn--sm" :disabled="isAdded(place)" @click="emit('add', place)">{{ isAdded(place) ? '넣음' : '넣기' }}</button>
        </div>
      </div>
    </div>
  </BottomSheet>
</template>

<style>
.aps-sheet { z-index: 41; gap: 10px; padding: 8px 16px 24px; }
.aps-head { display: flex; align-items: center; justify-content: space-between; }
.aps-head .h-title { margin: 0; }
.aps-base { margin: -6px 0 0; }
.aps-list { height: 320px; overflow-y: auto; }
.aps-list .row .btn { flex: none; }
</style>
