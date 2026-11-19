<script setup>
// S11-b 여행 상세 · 여행 날 (②): 오늘 여행의 일정, 미리 확인할 것(일정 점검), 챙길 것
// 주소의 ?id= 여행, 없으면 진행 중·오늘 여행을 서버에서 읽어요. 둘 다 없으면 안내.
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setDevClock } from '@/api/dev'
import { addDays, daysBetween, todayText } from '@/utils/dates'
import { devMode, syncClock } from '@/utils/devMode'
import { toMinutes, toTime } from '@/utils/time'
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import TopBar from '@/components/common/TopBar.vue'
import { useTripStore } from '@/stores/trip'
import { useTripDetail } from '@/utils/useTripDetail'
import { toShortDate, tripBadge } from '@/utils/tripView'
import { useStartTrip } from '@/utils/useStartTrip'
import StartConflictSheet from '@/components/trip/StartConflictSheet.vue'
import ChipScroller from '@/components/common/ChipScroller.vue'
import { useStopPhotos } from '@/utils/useStopPhotos'

const NIGHT_END = '04:00' // 이 시각 전 칸은 그날 밤 늦게 간 곳
// 정렬용 글자: 새벽 칸은 앞에 '1'을 붙여 그날 맨 뒤로 (11/12 ② 찬기, 자정 넘는 일정)
const nightLast = (time) => (time < NIGHT_END ? '1' : '0') + time

const route = useRoute()
const store = useTripStore()
const { saved, view, isLoading, error, load, loadDay } = useTripDetail()
const { conflict, start, retry } = useStartTrip()

// 여러 날 여행이면 1일차·2일차 고르기 (11/15: 예전엔 ?day=2로 들어올 때만 2일차가 보였음). 여행 전 상세와 같은 칩
const day = ref(1)
let hasStartDay = false // 처음 보여 줄 날을 정했는지 ([다시 시도]로 다시 읽어도 고른 날 그대로)
const dayList = computed(() => {
  if (!saved.value) return []
  let last = 1
  for (const stop of saved.value.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: toShortDate(addDays(saved.value.startDate, d - 1)) })
  return list
})

// 처음 볼 날: 주소의 ?day(여행 중 화면 "2일차 일정 보기"), 없으면 오늘이 이 여행의 며칠째인지 (시연 계정은 시연 시계 날짜)
//   시작 전이면 1일차, 끝난 뒤면 마지막 날. 그날 칸이 하나도 없으면 1일차
function findStartDay(trip) {
  const fromUrl = Number(route.query.day)
  let start = fromUrl > 0 ? fromUrl : daysBetween(trip.startDate, todayText()) + 1
  start = Math.min(Math.max(start, 1), dayList.value.length)
  const hasStops = trip.stops.some((stop) => (stop.day || 1) === start && stop.state !== 'skipped')
  return hasStops ? start : 1
}

// 여행을 읽고 볼 날의 일정으로 (처음 열 때와 [다시 시도])
async function openTrip(tripId) {
  await load(tripId)
  if (!saved.value) return
  if (!hasStartDay) {
    day.value = findStartDay(saved.value)
    hasStartDay = true
  }
  if (day.value > 1) await loadDay(day.value) // load는 늘 1일차라 다른 날이면 그날로
}

onMounted(async () => {
  await openTrip(route.query.id || store.tripId || (await store.findOngoingTripId()))
})

// 일차 칩: 같은 날이면 그대로, 아니면 그날 점검을 붙여 다시 그림 (늦게 온 앞 응답은 loadDay가 버림)
async function pickDay(d) {
  if (d === day.value) return
  day.value = d
  await loadDay(d)
}

// 칸 사진 (여행 전 상세 TripDetailView와 같은 방식, 11/19): 지금 보는 날 칸들로 받아요
//   일차 칩을 바꾸면 그날 칸 이름이 바뀌어서 useStopPhotos가 다시 받아요
const dayStops = computed(() => (saved.value ? saved.value.stops.filter((stop) => (stop.day || 1) === day.value) : []))
const stopPhotos = useStopPhotos(() => dayStops.value)
// 화면에 넘길 여행: 칸마다 찾은 사진을 img·photo에 덮어요 (사진이 없으면 photo는 null → StopThumb가 대신 그림)
const shownTrip = computed(() => (view.value ? {
  ...view.value,
  stops: view.value.stops.map((stop) => ({ ...stop, img: stopPhotos.value[stop.name] || stop.img, photo: stopPhotos.value[stop.name] || null })),
} : null))


// 여행 이어가기: 여행 중 화면으로. 시연 계정이 다음 날 일정을 보고 누르면 그날 아침으로 시계를 옮겨 그날을 시작 (11/08 주형)
const MORNING_BEFORE_MIN = 60 // 그날 첫 장소 몇 분 전으로 (숙소에서 일어난 때)
const router = useRouter()
async function continueTrip() {
  const pickedDay = day.value // 지금 칩으로 고른 날 (11/15: 예전엔 주소의 ?day)
  if (devMode.isOn && pickedDay > 1 && saved.value) {
    const date = addDays(saved.value.startDate, pickedDay - 1)
    const first = saved.value.stops.filter((stop) => (stop.day || 1) === pickedDay && stop.state !== 'skipped').sort((a, b) => nightLast(a.time).localeCompare(nightLast(b.time)))[0]
    if (first && todayText() < date) {
      const status = await setDevClock(date, toTime(toMinutes(first.time) - MORNING_BEFORE_MIN)).catch(() => null)
      if (status) syncClock(status)
    }
  }
  router.push('/live')
}
</script>

<template>
  <div class="scr scr-TripDetailToday">
    <TripPage v-if="view" :trip="shownTrip" :badge="tripBadge(saved)" back="/" :trip-id="saved.id" :reset-key="day">
      <template #head>
        <ChipScroller v-if="dayList.length > 1" class="td-days" role="tablist" aria-label="일차">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">
            {{ d.day }}일차<span class="t-cap tn" style="margin-left: 4px">{{ d.label }}</span>
          </button>
        </ChipScroller>
      </template>
      <template #below>
        <!-- 챙길 것: API가 아직 "확인 필요"라 이 화면에서만 적어 둬요 (새로고침하면 사라짐) -->
        <PackList :items="[]" />
      </template>
      <template #cta>
        <button v-if="saved.status === 'ongoing'" type="button" class="btn btn--primary btn--lg btn--block" @click="continueTrip">여행 이어가기</button>
        <div v-else class="cta__row">
          <RouterLink :to="'/plan/edit?id=' + view.id + '&day=' + day" class="btn btn--secondary btn--lg"><i class="ic i-edit" aria-hidden="true"></i>편집</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" :disabled="!view.stops.length" @click="start(saved.id)">여행 시작</button>
        </div>
      </template>
    </TripPage>
    <Teleport v-if="conflict" to=".scr-TripDetailToday .app">
      <StartConflictSheet :trip="conflict" @done="retry" @cancel="conflict = null" />
    </Teleport>
    <div v-else-if="!view" class="app">
      <TopBar title="오늘 여행" back="/" />
      <div class="scroll">
        <PageMessage v-if="isLoading" is-loading title="여행을 불러오는 중이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="openTrip(route.query.id || store.tripId)" />
        <PageMessage v-else title="오늘 여행이 없어요" text="새 여행을 만들거나 내 여행에서 골라 주세요" to="/trips" button="내 여행 보기" />
      </div>
    </div>
  </div>
</template>
