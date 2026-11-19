<script setup>
// S10-a 일정 점검 (②): 만든 일정을 지도와 목록으로 보고, 미리 확인할 것을 살핀 뒤 저장해요
// 초안(store.draft)에 장소가 있으면 서버가 점검(POST /api/plans/check: 실제 이동 시간·영업시간·고정 일정 여유)하고
// "저장"은 POST /api/trips. 초안이 비어 있으면(새로고침·주소 직접 입력) "아직 담은 장소가 없어요"와 장소 고르러 가기만 보여 줘요. (11/13 ② 찬기)
// 11/04: 여러 날이면 일차를 골라 그날만 점검(?day=N) · 목록 손잡이를 끌어 순서 바꾸기 → 시각을 비우고 다시 점검
//        · 지도는 점검 결과의 구간 경로(길찾기 API)로 진짜 길
//        · 오른쪽 위 버튼은 칸을 골랐을 때만 "○○ 편집"으로 떠서 그 칸 수정으로 가요 (순서는 목록에서 끌어서 바꿔요)
//        · 오른쪽 위 재생 버튼: 점이 일정대로 움직이고 시각이 흘러가요 (이동 미리보기, 화면에서만)
//        · 이동 줄을 눌러 도보·대중교통·택시를 고르면 그 칸 수단(mode)을 바꾸고 다시 점검 → 시간·경로가 진짜로 바뀜
import ChipScroller from '@/components/common/ChipScroller.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import TopBar from '@/components/common/TopBar.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { SHOW_PRICE_ESTIMATE, stopWon, toWonText } from '@/utils/budget'
import { useStopPhotos } from '@/utils/useStopPhotos'
import { modes } from '@/data/places'
import { checkPlan, createTrip, deleteTrip, finishTrip, getTripList } from '@/api/trips'
import { devMode } from '@/utils/devMode'
import { useTripStore } from '@/stores/trip'
import { addDays } from '@/utils/dates'
import { dayStopsOf, retime, setDayStops } from '@/utils/draftEdit'
import { toShortDate, toViewTrip, tripDetailLink } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { withEuroRo } from '@/utils/josa'
import { showTrashToast } from '@/utils/trash'
import { useRouteSim } from '@/utils/useRouteSim'
import RouteSimButton from '@/components/trip/RouteSimButton.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import TripWeather from '@/components/plan/TripWeather.vue'
import ScenarioRainNotice from '@/components/plan/ScenarioRainNotice.vue'

const route = useRoute()
const router = useRouter()
const store = useTripStore()

const check = ref(null) // 점검 결과 (고른 날)
const error = ref('')
const saving = ref(false)
const savedId = ref(null) // 다시 짜기로 방금 저장한 여행 번호 (있으면 바꿀지 묻는 창을 띄움)
const savedTrip = ref(null) // 다시 짜기로 방금 저장한 여행 (창을 닫고 갈 화면을 날짜로 고르려고)
const isReplacing = ref(false)
const day = ref(Number(route.query.day || 1))
const hasDraft = computed(() => store.draft.stops.length > 0)
// 점검 결과가 오기 전에는 초안 그대로 보여 줌. 초안이 비면 null (화면은 빈 안내만)
const baseTrip = computed(() => (hasDraft.value ? toViewTrip(store.draft, check.value, day.value, hoursCheckedIds.value) : null))
// 카드 사진: 장소마다 진짜 사진 (홈 카드와 같은 함수, 일정 안에서 안 겹침). 없으면 종류별 기본 사진
const stopPhotos = useStopPhotos(() => (hasDraft.value ? store.draft.stops.filter((stop) => (stop.day || 1) === day.value) : []))
// 칸 오른쪽 아래 금액 글자 (숙소처럼 넣지 않는 곳은 없음, 저장 전 초안에만)
function costTextOf(stop) {
  const won = stopWon(stop)
  return SHOW_PRICE_ESTIMATE && hasDraft.value && won !== null && won !== undefined ? toWonText(won) : null
}
const trip = computed(() => (baseTrip.value ? {
  ...baseTrip.value,
  stops: baseTrip.value.stops.map((stop) => ({ ...stop, img: stopPhotos.value[stop.name] || stop.img, photo: stopPhotos.value[stop.name] || null, costText: costTextOf(stop) })),
} : null))
// 일차 목록 [{ day: 1, label: '12/4(금)' }] (초안 칸의 가장 큰 day까지)
const dayList = computed(() => {
  let last = 1
  for (const stop of store.draft.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: store.draft.startDate ? toShortDate(addDays(store.draft.startDate, d - 1)) : '' })
  return list
})

// 고른 날 날짜 (날씨·옷차림 줄에 씀, 11/06)
const dayDate = computed(() => (store.draft.startDate ? addDays(store.draft.startDate, day.value - 1) : ''))

onMounted(runCheck)
// AI 일정 조건 화면(/plan/info)에서 만들어 왔으면 [조건 바꾸기] 버튼을 보여 줘요 (11/15)
//   만드는 중 화면은 주소를 바꾸며(replace) 넘어와서, 바로 전 기록이 조건 화면이에요 → 주소에 ?from=ai 표시
//   (일차 칩·칸 편집에 다녀와도 표시가 남아요. 왼쪽 위 ←는 그대로 내 여행)
onMounted(() => {
  const previous = window.history.state && window.history.state.back
  if (route.query.from === 'ai' || typeof previous !== 'string' || !previous.startsWith('/plan/info')) return
  router.replace({ query: { ...route.query, from: 'ai' } })
})
const isFromAi = computed(() => hasDraft.value && route.query.from === 'ai')
// [조건 바꾸기]: 조건 화면 1단계부터 (고른 값은 그대로라 [다음]만 눌러도 돼요)
//   조건 화면은 저장된 단계(store.planStep)로 열려서, 주소만 ?step=0 으로 주면 마지막 단계가 떠요 → 단계도 0으로
const isConditionsAsk = ref(false) // [조건 바꾸기]를 누르면 먼저 묻는 창 (다시 만들면 지금 일정이 바뀌어서)
function openConditions() {
  isConditionsAsk.value = false
  store.planStep = 0
  router.push({ path: '/plan/info', query: { step: '0', back: '/trips' } }) // 1단계에서 ← 를 누르면 내 여행으로 (일정 점검의 ←와 같게)
}

// 이동 미리보기. 일정이 바뀌면(다시 점검) 처음으로
const sim = useRouteSim(() => trip.value)
watch(check, sim.reset)

// 꼭 갈 곳인데 일정에 못 넣은 곳 (③ generate 응답). "다시 넣기"는 지금 일차 끝에 붙이고 다시 점검
const missedList = computed(() => store.draft.missed || [])
const missedText = computed(() => missedList.value.length + '곳 못 넣었어요: ' + missedList.value.map((place) => place.name).join(', '))
function addMissed() {
  const unknown = []
  for (const place of missedList.value) {
    if (place.lat == null) unknown.push(place)
    else store.addDraftPlace(place, false, null, day.value)
  }
  store.draft.missed = unknown
  if (unknown.length) showToast(unknown.length + '곳은 위치를 몰라요')
  runCheck()
}

// 그날 초안 점검: 시각이 빈 칸은 서버가 앞 일정에 이어서 채워 줌 → 그 시각을 초안에 반영
// 일차 칩을 빠르게 바꾸면 늦게 온 앞 요청이 지금 날 점검을 덮어써서, 마지막 요청 번호만 받아들여요 (11/16)
let lastCheckRequest = 0
async function runCheck() {
  if (!hasDraft.value) return
  const request = ++lastCheckRequest
  try {
    // 출발지(AI 일정의 "어디서 출발해요?")도 같이 보내서 1일차 첫 장소 앞에 길안내 (11/09, 저장은 안 함)
    const origin = store.planOrigin
    const draft = origin ? { ...store.draft, originName: origin.name, originLat: origin.lat, originLng: origin.lng } : store.draft
    const result = await checkPlan(draft, day.value)
    if (request !== lastCheckRequest) return // 그 사이 다른 날을 눌렀으면 이 응답은 버림
    check.value = result
    for (let i = 0; i < check.value.stops.length; i++) {
      const checked = check.value.stops[i]
      const stop = store.draft.stops.find((s) => s.name === checked.name && (s.day || 1) === checked.day)
      if (stop && !stop.time) stop.time = checked.time
    }
    error.value = ''
  } catch (e) {
    if (request === lastCheckRequest) error.value = e.message
  }
}

function pickDay(d) {
  if (d === day.value) return
  day.value = d
  check.value = null
  router.replace({ query: { ...route.query, day: String(d) } })
  runCheck()
}

// 목록에서 끌어 순서를 바꿈: order = 원래 번호를 새 순서로. 첫 시각·고정 칸만 두고 나머지는 비운 뒤 다시 점검
function reorder(order) {
  const list = dayStopsOf(store.draft, day.value)
  const startTime = list.length ? list[0].time : null
  const moved = order.map((i) => list[i])
  retime(moved, startTime)
  setDayStops(store.draft, day.value, moved)
  runCheck()
}

// 이동 수단을 바꿈: i번째 칸으로 오는 수단을 초안에 넣고, 시각을 비운 뒤 다시 점검 (시간·경로를 서버가 다시 계산)
async function changeMode(i, mode) {
  const list = dayStopsOf(store.draft, day.value)
  list[i].mode = mode
  retime(list, list[0].time)
  await runCheck()
  if (!error.value) showToast(withEuroRo(modes[mode].label) + ' 바꿨어요')
}

// 영업시간 [확인했어요]: 초안 그 칸에 hoursChecked를 붙여 다시 점검해도 접힌 채로 ("미리 확인할 것"에서도 빠짐)
// 칸 id는 화면 칸과 같은 규칙(id → placeId → 이름)이고, 그날 칸에서만 찾아서 이름이 같아도 다른 날은 안 바뀜
const hoursKeyOf = (stop) => stop.id ?? stop.placeId ?? stop.name
function markHoursChecked(id) {
  for (const stop of dayStopsOf(store.draft, day.value)) {
    if (hoursKeyOf(stop) === id) stop.hoursChecked = true
  }
}
const hoursCheckedIds = computed(() => dayStopsOf(store.draft, day.value).filter((stop) => stop.hoursChecked).map(hoursKeyOf))

// 고른 칸 편집 버튼: "성내동 카페 편집" → 그 칸 수정 (칸을 안 고르면 버튼이 없어요)
function editLabel(selected) {
  const stop = trip.value.stops[selected]
  if (!stop) return '' // 고른 칸이 목록에 없음 (일차를 바꿨거나 칸을 뺌)
  return stop.name.replace('천호 ', '') + ' 편집'
}
function editLink(selected) {
  return `/plan/edit/stop?day=${day.value}&idx=${selected}&back=${encodeURIComponent(route.fullPath)}` // 돌아올 곳 = 지금 주소 그대로
}

// 저장 끝: 초안·조건을 비우고 새 여행으로 (오늘·여행 중이면 여행 날 화면, 다음에 갈 여행이면 여행 전 화면)
function finishSave(trip) {
  store.resetDraft()
  store.resetPlan()
  showToast('일정을 저장했어요')
  router.replace(tripDetailLink(trip))
}

// 다시 짜기로 저장한 뒤: 옛 여행을 지우고 바꾸기 (지우기 실패해도 새 일정은 이미 저장됨)
async function replaceOld() {
  isReplacing.value = true
  const oldId = store.replanFrom.id
  let isMoved = false
  try {
    await deleteTrip(oldId)
    isMoved = true
  } catch (e) {
    showToast(e.message)
  }
  isReplacing.value = false
  finishSave(savedTrip.value)
  if (isMoved) showTrashToast(oldId) // 옛 여행은 휴지통으로 (되돌리면 두 여행 다 남아요)
}

// 시연 계정만 (11/08 주형: 몇 번을 하든 시나리오 날짜로, 겹치면 오래된 거 지우기): 새 여행과 날짜가 겹치는 예전 여행을 휴지통으로
//   영구 삭제가 아니라 휴지통(되돌리기 가능). 진행 중이면 먼저 끝냄. 다른 팀원이 쓰는 여행 번호는 건드리지 않음
const PROTECTED_TRIP_IDS = [51, 81, 82, 83, 84, 85, 86]
async function trashOverlappingTrips(saved) {
  const trips = await getTripList().catch(() => [])
  for (const trip of trips) {
    const isOverlap = trip.startDate <= saved.endDate && trip.endDate >= saved.startDate
    if (trip.id === saved.id || !isOverlap || PROTECTED_TRIP_IDS.includes(trip.id)) continue
    try {
      if (trip.status === 'ongoing') await finishTrip(trip.id)
      await deleteTrip(trip.id)
    } catch (e) {
      // 못 지운 여행은 그대로 둠 (새 일정 저장은 이미 끝남)
    }
  }
}

// 저장 전: 시각이 빈 칸이 있는 날은 점검으로 시각을 채워요 (11/16)
//   일정 점검은 보고 있는 날만 점검해서, 안 열어 본 날은 시각이 비어 있어요 → 서버가 "시각을 HH:mm으로 적어 주세요"로 막아요
async function fillEmptyTimes() {
  const days = [...new Set(store.draft.stops.filter((stop) => !stop.time).map((stop) => stop.day || 1))]
  const origin = store.planOrigin
  const draft = origin ? { ...store.draft, originName: origin.name, originLat: origin.lat, originLng: origin.lng } : store.draft
  for (const d of days) {
    const result = await checkPlan(draft, d)
    for (const checked of result.stops) {
      const stop = store.draft.stops.find((s) => s.name === checked.name && (s.day || 1) === checked.day)
      if (stop && !stop.time) stop.time = checked.time
    }
  }
}

async function save() {
  if (!hasDraft.value) return // 담은 장소가 없으면 저장할 것도 없음 (예전엔 가짜 일정을 저장한 척했어요)
  saving.value = true
  try {
    await fillEmptyTimes()
    const saved = await createTrip(store.draft)
    store.tripId = saved.id
    if (devMode.isOn) await trashOverlappingTrips(saved) // 시연 계정: 날짜가 겹치는 예전 여행은 휴지통으로
    if (store.replanFrom) {
      savedId.value = saved.id // 다시 짜기: 새 일정이 저장된 뒤에만 옛 여행을 어떻게 할지 물어봄
      savedTrip.value = saved
      return
    }
    finishSave(saved)
  } catch (e) {
    showToast(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="scr scr-PlanCheck">
    <!-- 담은 장소가 없을 때: 안내만 (지도·저장·장소 추가·편집 버튼은 안 보여요) -->
    <div v-if="!hasDraft" class="app">
      <TopBar title="일정 점검" back="/trips" />
      <div class="scroll">
        <PageMessage title="아직 담은 장소가 없어요" to="/plan/manual" button="장소 고르러 가기" />
      </div>
    </div>
    <TripPage v-else :trip="trip" back="/trips" :show-budget="hasDraft && SHOW_PRICE_ESTIMATE" :sim-me="sim.me.value" :active-stop="sim.activeStop.value" :reorderable="hasDraft" :mode-editable="hasDraft" @reorder="reorder" @change-mode="changeMode" :show-hours-help="hasDraft" @hours-checked="markHoursChecked" :reset-key="day">
      <template #right="{ selected }">
        <button v-if="isFromAi && selected === null && !sim.isOn.value" type="button" class="btn btn--sm btn--float" @click="isConditionsAsk = true"><i class="ic ic--s i-settings" aria-hidden="true"></i>조건 바꾸기</button>
        <RouteSimButton v-if="hasDraft" :sim="sim" />
        <RouterLink v-if="selected !== null && trip.stops[selected] && !sim.isOn.value" :to="editLink(selected)" class="btn btn--sm btn--float"><i class="ic ic--s i-edit" aria-hidden="true"></i>{{ editLabel(selected) }}</RouterLink>
      </template>
      <template v-if="hasDraft" #head>
        <div v-if="missedList.length" class="pc-missed">
          <i class="ic ic--s i-warning" aria-hidden="true"></i>
          <span class="pc-missed__text">{{ missedText }}</span>
          <button type="button" class="btn btn--secondary btn--sm" @click="addMissed">다시 넣기</button>
        </div>
        <ChipScroller v-if="dayList.length > 1" class="pc-days" role="tablist" aria-label="일차">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">
            {{ d.day }}일차<span v-if="d.label" class="t-cap tn" style="margin-left: 4px">{{ d.label }}</span>
          </button>
        </ChipScroller>
        <TripWeather compact :date="dayDate" :stops="dayStopsOf(store.draft, day)" />
        <ScenarioRainNotice :draft="store.draft" @applied="runCheck" /><!-- 시연 계정만: 비 예보를 일정 짤 때 알려 주고 반영 (③ 11/08) -->
      </template>
      <template v-if="error" #below>
        <p class="t-cap c-danger" style="padding: 12px 16px">{{ error }}</p>
      </template>
      <template #cta>
        <div class="cta__row">
          <RouterLink to="/plan/manual" class="btn btn--secondary btn--lg"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" :disabled="saving" @click="save">이 일정으로 저장</button>
        </div>
      </template>
    </TripPage>
    <Teleport v-if="isConditionsAsk" to=".scr-PlanCheck .app">
      <div class="pc-ask">
        <ConfirmModal title="조건을 바꿔 다시 만들까요?" text="다시 만들면 조건이 같아도 장소가 바뀔 수 있어요. 지금 일정과 직접 바꾼 순서·이동 수단도 새 일정으로 바뀌어요." ok-text="조건 바꾸기" @cancel="isConditionsAsk = false" @ok="openConditions" />
      </div>
    </Teleport>
    <Teleport v-if="savedId" to=".scr-PlanCheck .app">
      <div class="dim" style="z-index: 40"></div>
      <section class="pc-replace" role="alertdialog" aria-modal="true" aria-label="지금 여행 바꾸기">
        <h2 class="h-title" style="margin: 0">지금 여행을 이걸로 바꿀까요?</h2>
        <p class="t-cap" style="margin: 0 0 12px">{{ store.replanFrom.title }}</p>
        <div class="cta__row">
          <button type="button" class="btn btn--secondary btn--lg" :disabled="isReplacing" @click="finishSave(savedTrip)">둘 다 두기</button>
          <button type="button" class="btn btn--primary btn--lg" :disabled="isReplacing" @click="replaceOld">{{ isReplacing ? '바꾸는 중' : '바꾸기' }}</button>
        </div>
      </section>
    </Teleport>
  </div>
</template>

<style>
.scr-PlanCheck .pc-ask{position:absolute;inset:0;z-index:40} /* 묻는 창을 바텀시트·지도 버튼보다 위에 (ConfirmModal은 z-index 8~9라 그냥 두면 시트 아래로 깔림) */
.scr-PlanCheck .pc-ask .modal{min-height:0} /* 공통 .modal은 min-height 404px라 글만 있는 창은 아래가 비어서, 이 화면에서만 0 */
.scr-PlanCheck .pc-missed{display:flex;align-items:center;gap:8px;margin:0 16px 8px;padding:8px 12px;border-radius:10px;font-size:13px;font-weight:600;color:var(--warning);background:var(--warning-soft)}
.scr-PlanCheck .pc-missed .ic{flex:none}
.scr-PlanCheck .pc-missed__text{flex:1;min-width:0}
.scr-PlanCheck .pc-missed .btn{flex:none}
.scr-PlanCheck .pc-replace{position:absolute;left:0;right:0;bottom:0;z-index:41;display:flex;flex-direction:column;gap:8px;padding:24px 16px;border-radius:20px 20px 0 0;background:#fff}
.scr-PlanCheck .pc-replace .btn{flex:1}
.scr-PlanCheck .pc-days{padding:0 16px 8px}
.scr-PlanCheck .pc-days .chip.is-on .t-cap{color:inherit}
</style>
