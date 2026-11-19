<script setup>
// S10-a2 일정 편집 모드 (②): 손잡이로 순서 바꾸기, 빨간 버튼으로 빼기, 칸을 눌러 한 칸 수정
// 고정 일정(예약)·숙소·이미 다녀온 칸은 자리를 안 바꿔요
// 두 가지로 들어와요 (11/04)
//   - 일정 점검에서(?day=N): 새로 짠 초안(store.draft)을 고치고, "완료"면 점검으로 돌아가 시각을 다시 계산
//   - 저장된 여행 상세에서(?id=12): 서버에서 읽은 복사본(store.editTrip)을 고치고, "완료"면 서버에 한 번에 저장
//     (빈 시각은 점검으로 채운 뒤 PUT /api/trips/{id}/stops)
// 순서를 바꾸면 첫 칸·고정 칸 시각만 두고 나머지는 비워요 (utils/draftEdit). ✕는 들어오기 전으로 되돌려요
// 고정 칸(예약) 위에 놓으면 그 앞·뒤에 끼워 넣어요 (예약 전에 다른 곳 먼저 가기)
// 지도 + 위에 뜬 바텀시트 (11/15: 여행 상세와 같은 모양. 예전엔 위 36% 지도·아래 목록): 번호 핀 + 순서대로 잇는 선
//   시트 머리(일차 칩·안내·이동 비교)는 그대로 있고 목록만 스크롤. 칸을 누르면 핀이 커지고(한 번 더 누르면 한 칸 수정), 핀을 누르면 그 칸으로 스크롤
// − 는 바로 빼고 토스트로 되돌려요 (완료 전에는 서버에 안 가요). 지도의 [동선 최적화]는 상세 화면과 같은 함수
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { importance } from '@/data/places'
import { checkPlan } from '@/api/trips'
import KakaoMap from '@/components/common/KakaoMap.vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { useRouteOptimize } from '@/utils/useRouteOptimize'
import { useTripStore } from '@/stores/trip'
import { goBackTo } from '@/utils/back'
import { addDays } from '@/utils/dates'
import { cancelEdit, dayStopsOf, dropPlaceOf, editingTripOf, finishEdit, hasEditChanges, loadEditTrip, orderAfterDrop, retime, saveEditTrip, setDayStops, startEdit, toEditRow } from '@/utils/draftEdit'
import { toDuration } from '@/utils/time'
import { toShortDate, tripDetailLink } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { useStopPhotos } from '@/utils/useStopPhotos'
import StopThumb from '@/components/trip/StopThumb.vue'
import AddPlaceSheet from '@/components/trip/AddPlaceSheet.vue'
import { distanceMeters } from '@/utils/geo'
import { isStayStop } from '@/utils/mapView'

const NEW_STAY_MIN = 60 // [장소 추가]로 넣는 칸의 머무는 시간 (stores/trip.js addDraftPlace와 같음)
const SEOUL_CENTER = { lat: 37.5665, lng: 126.978 } // 좌표 있는 칸이 하나도 없을 때 찾는 기준

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const tripId = route.query.id || null // 있으면 저장된 여행 편집, 없으면 새로 짠 초안 편집
const day = ref(Number(route.query.day || 1))
const isLoading = ref(!!tripId)
const isSaving = ref(false)
const loadError = ref('')

const trip = computed(() => editingTripOf(store, tripId))
const hasTrip = computed(() => !!trip.value && trip.value.stops.length > 0)
const rows = computed(() => (hasTrip.value ? dayStopsOf(trip.value, day.value).map(toEditRow) : []))
// 일차 목록 [{ day: 1, label: '12/4(금)' }] (여행 날짜 수만큼, 하루면 안 보여 줌)
const dayList = computed(() => {
  if (!hasTrip.value) return []
  let last = countDays(trip.value)
  for (const stop of trip.value.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: trip.value.startDate ? toShortDate(addDays(trip.value.startDate, d - 1)) : '' })
  return list
})
const stopPhotos = useStopPhotos(() => (hasTrip.value ? dayStopsOf(trip.value, day.value) : [])) // 칸 사진 (홈 카드와 같은 함수)
// 지도는 상단 바 아래(103px)부터 화면 끝까지, 시트는 화면 440px부터 → 지도 아래 약 404px가 시트에 가려서 핀을 그 위에 맞춤 (+여유 20)
const MAP_PADDING = { top: 36, right: 36, bottom: 424, left: 36 }
const mapRef = ref(null)
const selected = ref(null) // 고른 칸 번호
const mapStops = computed(() => (hasTrip.value ? dayStopsOf(trip.value, day.value) : []))
const mapPins = computed(() => mapStops.value.map((stop, i) => ({
  key: i,
  index: i,
  lat: stop.lat,
  lng: stop.lng,
  text: i + 1,
  cls: selected.value === i ? 'pin--sel' : '',
  label: i + 1 + ' ' + stop.name,
})))
// 칸을 누름: 처음엔 핀을 키우고 지도 가운데로, 이미 고른 칸을 또 누르면 한 칸 수정
function pickRow(i) {
  if (selected.value === i) return openStop(i)
  selected.value = i
  const stop = mapStops.value[i]
  if (mapRef.value && stop.lat != null) mapRef.value.flyTo(stop.lat, stop.lng) // padding을 보고 시트에 안 가리는 칸 가운데로 (moveTo는 지도 한가운데라 핀이 시트 뒤에 숨음)
}
// 핀을 누름: 그 칸으로 스크롤
async function pickPin(pin) {
  selected.value = pin.index
  await nextTick()
  document.querySelector(`.scr-PlanEdit .erow[data-idx="${pin.index}"]`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}
// 이동 시간 비교: 처음 값과 지금 값 (순서·칸이 바뀔 때마다 점검으로 다시 재요)
const firstMove = ref(null)
const nowMove = ref(null)
let lastMeasure = 0 // 순서를 빠르게 여러 번 바꾸면 마지막 점검 결과만 시각에 써요
async function measureMove(isFill = false) {
  const request = ++lastMeasure
  try {
    const check = await checkPlan(trip.value, day.value)
    if (isFill && request === lastMeasure) fillTimes(check.stops)
    let minutes = 0
    for (const leg of check.legs || []) minutes += leg.minutes || 0
    nowMove.value = minutes
    if (firstMove.value === null) firstMove.value = minutes
    return minutes
  } catch {
    return null
  }
}
// 순서를 바꾸거나 칸을 빼면 비워 둔 시각(retime)을 점검 결과로 바로 채워요 ([완료] 전에도 새 시각이 보이게, 11/16)
function fillTimes(checkedStops) {
  const list = dayStopsOf(trip.value, day.value)
  for (const checked of checkedStops || []) {
    const stop = list.find((s) => s.name === checked.name)
    if (stop && !stop.time) stop.time = checked.time
  }
}
watch(() => day.value + '/' + mapStops.value.map((stop) => stop.name).join('|'), (key, oldKey) => {
  if (!mapStops.value.length) return
  const isSameDay = !!oldKey && key.split('/')[0] === oldKey.split('/')[0]
  if (oldKey && !isSameDay) firstMove.value = null // 다른 날로 바꾸면 처음부터
  measureMove(isSameDay) // 같은 날에서 순서·칸이 바뀐 때만 시각을 채움 (처음 열 때·날 바꿀 때는 그대로)
}, { immediate: true })
// 지도의 [동선 최적화]: 누를 때마다 다음 후보로 (저장은 "완료"할 때)
const optimize = useRouteOptimize({
  stopsOf: () => dayStopsOf(trip.value, day.value),
  dateOf: () => addDays(trip.value.startDate, day.value - 1),
  transportsOf: () => trip.value.transports,
  apply: applyOrder,
})
async function applyOrder(list) {
  const before = nowMove.value
  const backup = JSON.parse(JSON.stringify(trip.value.stops))
  setDayStops(trip.value, day.value, list)
  selected.value = null
  const after = await measureMove()
  showToast(`이동 ${before}분 → ${after}분`, {
    isTop: true,
    actionText: '되돌리기',
    action: () => {
      optimize.reset()
      trip.value.stops = backup
    },
  })
}
const dragIndex = ref(null) // 끌고 있는 칸 번호
const dropTo = ref(null) // 놓을 칸 번호
const dropPlace = ref(null) // 'swap' 자리 바꾸기 / 'before'·'after' 고정 칸 앞·뒤에 끼우기
const dragY = ref(0) // 끄는 칸이 손가락을 따라 움직인 거리(px)
let startY = 0
let scale = 1 // 폰 화면을 줄여 보여 줄 때 손가락 거리 → 화면 거리 비율

onMounted(async () => {
  router.replace(hereLink()) // 주소에 day를 늘 붙여 둠 (한 칸 수정에서 "뒤로"로 돌아올 때 주소가 같게)
  if (!tripId) {
    if (hasTrip.value) startEdit(trip.value)
    return
  }
  try {
    await loadEditTrip(store, tripId)
    startEdit(store.editTrip)
  } catch (e) {
    loadError.value = e.message
  }
  isLoading.value = false
})

// 여행 날짜 수 (시작일 ~ 끝날 포함, 날짜가 없으면 1)
function countDays(t) {
  if (!t.startDate || !t.endDate) return 1
  return Math.round((new Date(t.endDate) - new Date(t.startDate)) / 86400000) + 1
}

// 지금 편집 화면 주소 (한 칸 수정·빼기에서 돌아올 곳)
function hereLink() {
  return '/plan/edit?' + (tripId ? 'id=' + tripId + '&' : '') + 'day=' + day.value
}
// 다른 날 고르기: 고른 칸 번호는 "그날 몇 번째"라 다른 날로 가면 비움 (안 비우면 다음 날 같은 번호 칸이 골라진 채로 남음)
function pickDay(d) {
  if (d === day.value) return
  selected.value = null
  day.value = d
  router.replace(hereLink())
}

// 끌어 놓기 결과 반영: 그날 칸을 새 순서로 바꿔 넣고 시각을 다시 맞춤
function moveRow(from, to, place) {
  const list = dayStopsOf(trip.value, day.value)
  const startTime = list[0].time
  const moved = orderAfterDrop(list.length, from, to, place).map((i) => list[i])
  retime(moved, startTime)
  setDayStops(trip.value, day.value, moved)
  selected.value = null
  optimize.reset()
}
// 끌기: 다른 칸은 그대로, 끄는 칸만 손가락을 따라 움직이고 놓은 자리의 칸과 자리를 바꿔요 (11/04)
// 움직임·놓기는 화면 전체(window)에서 받아요 (손가락이 손잡이 밖으로 나가도 계속)
function startDrag(i, e) {
  e.preventDefault()
  const row = e.currentTarget.closest('.erow')
  scale = row.getBoundingClientRect().height / row.offsetHeight || 1
  dragIndex.value = i
  dropTo.value = null
  dragY.value = 0
  startY = e.clientY
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}
function stopListening() {
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
}
onUnmounted(stopListening)
function onDrag(e) {
  if (dragIndex.value === null) return
  dragY.value = (e.clientY - startY) / scale
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('.erow')
  const target = row ? Number(row.dataset.idx) : null
  dropPlace.value = dropPlaceOf(rows.value, dragIndex.value, target)
  dropTo.value = dropPlace.value ? target : null
}
function endDrag() {
  stopListening()
  const from = dragIndex.value
  const to = dropTo.value
  const place = dropPlace.value
  dragIndex.value = null
  dropTo.value = null
  dropPlace.value = null
  dragY.value = 0
  if (from !== null && to !== null) moveRow(from, to, place)
}

// 키보드 위아래 화살표로도 옆 칸과 바꿔요 (고정 칸이면 그 너머로)
function moveBy(i, step) {
  const to = i + step
  if (to < 0 || to >= rows.value.length) return
  const place = dropPlaceOf(rows.value, i, to)
  if (place) moveRow(i, to, place)
}

// 한 칸 수정·빼기 열기 (몇째 날 몇 번째 칸, 돌아올 곳)
function sheetLink(path, i) {
  return `${path}?${tripId ? 'id=' + tripId + '&' : ''}day=${day.value}&idx=${i}&back=${encodeURIComponent(hereLink())}`
}
function openStop(i) {
  router.push(sheetLink('/plan/edit/stop', i))
}
// − : 바로 빼고, 토스트의 되돌리기로 살려요 (완료 전에는 서버에 안 가요)
function removeRow(i) {
  const list = dayStopsOf(trip.value, day.value)
  const backup = JSON.parse(JSON.stringify(trip.value.stops))
  const startTime = list[0].time
  const name = list[i].name
  const rest = list.filter((stop, index) => index !== i)
  retime(rest, startTime)
  setDayStops(trip.value, day.value, rest)
  selected.value = null
  optimize.reset()
  showToast(name + ' 뺐어요', { actionText: '되돌리기', action: () => { trip.value.stops = backup } })
}

// [장소 추가] 시트 (11/19): 이 화면 안에서 찾아 넣어요. 저장된 여행도 [완료] 때 한 번에 저장
const isAddOpen = ref(false)
// 찾는 기준 위치: 고른 칸이 있으면 그 칸, 없으면 그날 칸들 가운데 (그날 좌표가 없으면 여행 전체 가운데)
const addBase = computed(() => {
  const picked = selected.value !== null ? mapStops.value[selected.value] : null
  if (picked && picked.lat != null) return { lat: picked.lat, lng: picked.lng, name: picked.name }
  const dayPoints = mapStops.value.filter((stop) => stop.lat != null)
  const points = dayPoints.length ? dayPoints : trip.value.stops.filter((stop) => stop.lat != null)
  return { ...centerOf(points), name: '그날 장소들 가운데' }
})
// 여러 칸의 가운데 (위도·경도 평균)
function centerOf(points) {
  if (!points.length) return SEOUL_CENTER
  let lat = 0
  let lng = 0
  for (const point of points) {
    lat += point.lat
    lng += point.lng
  }
  return { lat: lat / points.length, lng: lng / points.length }
}
// [넣기]: 그날 목록에 넣고 시각을 다시 맞춤. 토스트의 되돌리기로 빼요 (완료 전에는 서버에 안 가요)
function addPlace(place) {
  const list = dayStopsOf(trip.value, day.value)
  const backup = JSON.parse(JSON.stringify(trip.value.stops))
  const startTime = list.length ? list[0].time : null
  const stop = toNewStop(place)
  const next = [...list]
  next.splice(insertIndexOf(list, stop), 0, stop)
  retime(next, startTime)
  setDayStops(trip.value, day.value, next)
  selected.value = null
  optimize.reset()
  isAddOpen.value = false
  showToast(place.name + ' 넣었어요', { actionText: '되돌리기', action: () => { trip.value.stops = backup } })
}
// 새 칸 모양 (stores/trip.js addDraftPlace와 같게. 시각은 비워 두면 점검이 채움)
function toNewStop(place) {
  return { day: day.value, placeId: place.id ?? null, name: place.name, category: place.category ?? null, address: place.address ?? null, lat: place.lat, lng: place.lng, time: null, stay: NEW_STAY_MIN, fixed: false, mode: null, memo: null }
}
// 넣을 자리(그날 몇 번째 앞): 고른 칸이 있으면 바로 뒤, 없으면 동선(직선거리 합)이 제일 덜 늘어나는 곳
function insertIndexOf(list, stop) {
  const range = insertRange(list)
  if (selected.value !== null) return Math.min(Math.max(selected.value + 1, range.min), range.max)
  let bestIndex = range.max
  let bestAdded = Infinity
  for (let i = range.min; i <= range.max; i++) {
    // 앞 칸 → 새 칸 → 뒤 칸으로 돌아가면 앞 칸 → 뒤 칸보다 얼마나 늘어나는지 (list[-1]·list[끝]은 없음 → 0)
    const added = gapMeters(list[i - 1], stop) + gapMeters(stop, list[i]) - gapMeters(list[i - 1], list[i])
    if (added < bestAdded) {
      bestAdded = added
      bestIndex = i
    }
  }
  return bestIndex
}
// 넣을 수 있는 자리 범위: 다녀온 칸 뒤부터(그날 첫 칸이 숙소면 그 뒤부터), 그날 마지막이 숙소면 그 앞까지
function insertRange(list) {
  let min = list.length && isStayStop(list[0]) ? 1 : 0 // 아침 숙소에서 나오기 전에 다른 곳을 넣지 않게
  for (let i = 0; i < list.length; i++) {
    if (list[i].state === 'done') min = i + 1
  }
  let max = list.length
  if (list.length && isStayStop(list[list.length - 1])) max = list.length - 1
  return { min, max: Math.max(min, max) }
}
// 두 칸 사이 직선거리(m). 칸이 없거나 좌표가 없으면 0
function gapMeters(a, b) {
  if (!a || !b || a.lat == null || b.lat == null) return 0
  return distanceMeters(a.lat, a.lng, b.lat, b.lng)
}

// 저장된 여행이면 상세로, 초안이면 일정 점검으로
function exitLink() {
  if (!tripId) return '/plan/check?day=' + day.value
  return trip.value ? tripDetailLink(trip.value) : '/trips'
}

// ✕: 고친 게 있으면 버릴지 먼저 물어봐요 (모르고 누르면 고친 순서·시간이 다 사라져서)
const isCancelAsk = ref(false)
function onCloseClick() {
  if (hasEditChanges(trip.value)) isCancelAsk.value = true
  else cancel()
}

function cancel() {
  isCancelAsk.value = false
  const link = exitLink() // 복사본을 지우기 전에 돌아갈 곳부터 정함
  cancelEdit(trip.value)
  if (tripId) store.editTrip = null
  goBackTo(link)
}

async function done() {
  if (!tripId) {
    finishEdit()
    showToast('일정을 바꿨어요 · 시간을 다시 계산해요')
    goBackTo(exitLink())
    return
  }
  const link = exitLink()
  isSaving.value = true
  try {
    await saveEditTrip(store)
    showToast('일정을 저장했어요')
    goBackTo(link) // 상세로 돌아가면 상세가 서버에서 다시 읽어요
  } catch (e) {
    showToast(e.message)
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <div class="scr scr-PlanEdit">
    <div class="app app--white">
      <header class="topbar" style="padding-right: 16px">
        <button type="button" class="iconbtn" aria-label="편집 취소" @click="onCloseClick"><i class="ic ic--l i-close" aria-hidden="true"></i></button>
        <h1 class="topbar__title">일정 편집{{ dayList.length > 1 ? ' · ' + day + '일차' : '' }}</h1>
        <button type="button" class="btn btn--primary btn--sm" :disabled="isLoading || isSaving || !hasTrip" @click="done">{{ isSaving ? '저장 중' : '완료' }}</button>
      </header>

      <div v-if="mapStops.length" class="pe-map">
        <KakaoMap ref="mapRef" :pins="mapPins" :padding="MAP_PADDING" :fit-key="day + '/' + mapStops.length" @pick="pickPin" />
      </div>
      <!-- 불러오는 중 / 못 불러옴 / 고칠 일정 없음: 예전처럼 글자만 -->
      <div v-if="isLoading || loadError || !hasTrip" class="scroll" style="padding-top: 4px; gap: 10px; bottom: 34px">
        <p v-if="isLoading" class="t-cap">일정을 불러오는 중이에요</p>
        <p v-else-if="loadError" class="t-cap c-warning">{{ loadError }}</p>
        <p v-else class="t-cap">고칠 일정이 없어요</p>
      </div>
      <!-- 일정이 있으면 여행 상세와 같은 시트 (손잡이로 맨 위 / 중간 / 손잡이만) -->
      <BottomSheet v-else class="sheet--peek trip-sheet" expandable aria-label="일정 편집 목록">
        <!-- 시트 머리: 여기를 잡고 시트를 끌어요 (data-drag). 칩 버튼은 그대로 눌려요. 목록을 내려도 이 줄은 그대로 -->
        <div class="pe-head" data-drag>
          <div v-if="dayList.length > 1" class="pe-days" role="tablist" aria-label="며칠째">
            <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': d.day === day }" :aria-selected="d.day === day" @click="pickDay(d.day)">{{ d.day }}일차<span class="t-cap tn">{{ d.label }}</span></button>
          </div>
          <p class="t-cap" style="display: flex; align-items: center; gap: 4px"><i class="ic ic--s i-grip" aria-hidden="true"></i>손잡이를 끌어 순서를 바꿔요 · 시각은 다시 계산해요</p>
          <p v-if="firstMove !== null && nowMove !== firstMove" class="t-cap tn" style="margin: 0">이동 {{ firstMove }}분 → {{ nowMove }}분</p>
          <!-- 장소 추가: 이 화면 위로 "장소 넣기" 시트를 열어요 (초안·저장된 여행 둘 다, 11/19). 시트 머리에 있어서 목록을 내려도 늘 보여요 -->
          <button type="button" class="btn btn--secondary btn--md btn--block" @click="isAddOpen = true"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</button>
        </div>
        <!-- 시트 몸통: 목록만 스크롤 -->
        <div class="trip-scroll">
        <ol class="elist">
          <li
            v-for="(stop, i) in rows"
            :key="stop.key"
            class="erow"
            :class="{ 'is-sel': selected === i, 'is-drag': dragIndex === i, 'is-target': dropTo === i && dropPlace === 'swap', 'is-before': dropTo === i && dropPlace === 'before', 'is-after': dropTo === i && dropPlace === 'after' }"
            :style="dragIndex === i ? { transform: 'translateY(' + dragY + 'px)' } : null"
            :data-idx="i"
          >
            <button v-if="!stop.isDone" type="button" class="delbtn" :aria-label="stop.name + ' 빼기'" @click="removeRow(i)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
            <span v-else class="delbtn" style="visibility: hidden" aria-hidden="true"></span>
            <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed }" style="grid-template-columns: 1fr" @click="pickRow(i)">
              <span class="stop__card">
                <span class="pe-num tn" aria-hidden="true">{{ i + 1 }}</span>
                <StopThumb :src="stopPhotos[stop.name]" :name="stop.name" :category="stop.category" />
                <span class="stop__body">
                  <span class="stop__name">{{ stop.name }}</span>
                  <span v-if="stop.isDone" class="stop__meta c-success"><i class="ic ic--s i-check" aria-hidden="true"></i>{{ stop.time }} · 다녀왔어요</span>
                  <span v-else-if="stop.fixed" class="stop__meta c-navy">{{ stop.time }} · 시간 고정</span>
                  <span v-else-if="stop.isStay" class="stop__meta c-navy"><i class="ic ic--s i-home" aria-hidden="true"></i>숙소</span>
                  <span v-else class="stop__meta">{{ stop.time || '시각 다시 계산' }} · {{ toDuration(stop.dur) }}</span>
                </span>
                <span v-if="!stop.fixed && !stop.isStay" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              </span>
            </button>
            <span v-if="!stop.movable" class="grip" style="opacity: 0.35; cursor: default" :aria-label="stop.isDone ? '다녀온 곳은 순서가 그대로예요' : stop.isStay ? '숙소는 순서가 그대로예요' : '고정 일정은 순서가 그대로예요'">
              <i class="ic ic--l" :class="stop.isDone ? 'i-check' : stop.isStay ? 'i-home' : 'i-clock'" aria-hidden="true"></i>
            </span>
            <button
              v-else
              type="button"
              class="grip"
              :aria-label="stop.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
              @pointerdown="startDrag(i, $event)"
              @keydown.up.prevent="moveBy(i, -1)"
              @keydown.down.prevent="moveBy(i, 1)"
            >
              <i class="ic ic--l i-grip" aria-hidden="true"></i>
            </button>
          </li>
        </ol>
        </div>
      </BottomSheet>
      <AddPlaceSheet v-if="isAddOpen" :base="addBase" :day-stops="mapStops" @add="addPlace" @close="isAddOpen = false" />
      <div v-if="isCancelAsk" class="pe-ask">
        <ConfirmModal title="고친 내용을 버릴까요?" text="지금 나가면 바꾼 순서·시간·뺀 장소가 모두 편집 전으로 돌아가요." ok-text="버리기" cancel-text="계속 고치기" danger @cancel="isCancelAsk = false" @ok="cancel" />
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanEdit .pe-ask{position:absolute;inset:0;z-index:40} /* 버릴지 묻는 창을 바텀시트 위에 */
.scr-PlanEdit .pe-ask .modal{min-height:0} /* 공통 .modal의 min-height 404px 때문에 아래가 비는 걸 이 화면에서만 없앰 */
.scr-PlanEdit .pe-map { position: absolute; top: 103px; left: 0; right: 0; bottom: 0; z-index: 1; } /* 상단 바 아래부터 화면 끝까지 (위에 시트가 뜸) */
.scr-PlanEdit .pe-map__btn { position: absolute; top: 10px; right: 10px; z-index: 2; }
.scr-PlanEdit .pe-head { display: flex; flex-direction: column; gap: 8px; padding: 0 16px; touch-action: none; } /* 시트 머리: 끌 때 화면이 스크롤되지 않게 */
.scr-PlanEdit .pe-num { flex: none; width: 20px; height: 20px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--navy); color: #fff; font-size: 11px; font-weight: 700; }
.scr-PlanEdit .erow.is-sel .stop__card { border-color: var(--navy); }
.scr-PlanEdit .elist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.scr-PlanEdit .grip { touch-action: none; }
.scr-PlanEdit .pe-days { display: flex; gap: 6px; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; }
.scr-PlanEdit .pe-days .chip { gap: 4px; flex: none; }
.scr-PlanEdit .pe-days .chip.is-on .t-cap { color: inherit; }
.scr-PlanEdit .erow.is-drag { position: relative; z-index: 5; pointer-events: none; }
.scr-PlanEdit .erow.is-drag .stop__card { border-color: var(--navy); background: #fff; box-shadow: 0 8px 20px rgba(24, 33, 58, 0.22); }
.scr-PlanEdit .erow.is-target .stop__card { border: 2px dashed var(--navy); background: var(--navy-soft); }
.scr-PlanEdit .erow.is-before .stop__card { background: var(--navy-soft); box-shadow: inset 0 4px 0 var(--navy); }
.scr-PlanEdit .erow.is-after .stop__card { background: var(--navy-soft); box-shadow: inset 0 -4px 0 var(--navy); }
</style>
