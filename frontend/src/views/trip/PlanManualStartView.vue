<script setup>
// 직접 고르기 시작 (②): 질문 없이 한 화면에서 어디(구 지도)·언제(날짜·시작 시각)만 고르고 바로 지도 화면으로
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import GuMapPicker from '@/components/plan/GuMapPicker.vue'
import RangeCalendar from '@/components/plan/RangeCalendar.vue'
import { useTripStore } from '@/stores/trip'
import { showToast } from '@/utils/toast'
import { daysBetween } from '@/utils/dates'

const DEFAULT_START = '10:00'
const MANUAL_PATH = '/plan/manual' // 지도 화면 (이 시작 화면 주소도 이걸로 시작해요)
const router = useRouter()
const store = useTripStore()

// 지도 화면에서 돌아왔나: 바로 앞(back)이나 바로 뒤(forward) 기록이 직접 고르기 화면이면 돌아온 것 (11/13 ② 찬기)
//   위쪽 뒤로 가기(goBack)는 이 화면으로 바꿔 들어와서 back이 '/plan/manual/start', 폰·브라우저 뒤로 가기는 forward가 '/plan/manual'
function isReturning() {
  const state = window.history.state || {}
  return isManualPath(state.back) || isManualPath(state.forward)
}
function isManualPath(path) {
  return typeof path === 'string' && path.startsWith(MANUAL_PATH)
}

// 새로 들어온 경우(/new에서 [지도에서 직접 고르기])에만 비워요. 돌아왔으면 구·날짜·시작 시각·담은 장소를 그대로 둬요
if (!isReturning()) {
  store.resetDraft()
  store.resetPlan()
}
const startTime = ref(store.planStartTime || DEFAULT_START)

const canNext = computed(() => store.planAreas.length > 0 && !!store.planStartDate)

// 고른 구 저장 (planArea는 일정 만들기 흐름이 같이 씀)
function setAreas(areas) {
  store.planAreas = areas
  const first = areas[0]
  store.planArea = first ? { name: first.name, fullName: '서울 ' + first.name, type: 'gu', lat: first.lat, lng: first.lng } : null
}

function titleOf(endDate) {
  const nights = daysBetween(store.planStartDate, endDate)
  const names = store.planAreas.map((area) => area.name).join('·')
  return names + (nights > 0 ? ` ${nights}박 ${nights + 1}일` : ' 하루')
}

function goNext() {
  if (!store.planAreas.length) return showToast('구를 골라 주세요')
  if (!store.planStartDate) return showToast('날짜를 골라 주세요')
  const endDate = store.planEndDate || store.planStartDate
  const stops = keepStopsIn(daysBetween(store.planStartDate, endDate) + 1)
  store.setDraft({ title: titleOf(endDate), startDate: store.planStartDate, endDate, people: store.planPeople || 2, transports: ['subway', 'bus', 'walk'], stops })
  store.planStartTime = startTime.value
  router.push(MANUAL_PATH)
}

// 이미 담은 칸은 이어 붙여요. 날짜를 줄여서 없어진 날의 칸만 빼고 알려 줌
function keepStopsIn(dayCount) {
  const kept = []
  for (const stop of store.draft.stops) {
    if ((stop.day || 1) <= dayCount) kept.push(stop)
  }
  const removed = store.draft.stops.length - kept.length
  if (removed > 0) showToast(`날짜가 줄어서 ${removed}곳을 뺐어요`)
  return kept
}
</script>

<template>
  <div class="scr scr-ManualStart">
    <div class="app">
      <TopBar title="어디로, 언제 가요?" back="/new" />
      <div class="scroll" style="bottom: 104px; gap: 12px; padding-top: 4px">
        <GuMapPicker :model-value="store.planAreas" @update:model-value="setAreas" />
        <section class="card card--pad" aria-label="날짜">
          <RangeCalendar v-model:start="store.planStartDate" v-model:end="store.planEndDate" />
        </section>
        <section class="card card--pad ms-time">
          <label for="ms-start" class="t-strong">시작 시각</label>
          <input id="ms-start" v-model="startTime" type="time" class="ms-time__input tn" />
        </section>
      </div>
      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :class="{ 'is-disabled': !canNext }" @click="goNext">지도에서 고르기</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-ManualStart .ms-time { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.scr-ManualStart .ms-time__input { height: 44px; padding: 0 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface-raised); font: inherit; font-size: 16px; color: var(--ink); }
</style>
