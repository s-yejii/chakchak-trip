<script setup>
import { toDuration } from '@/utils/time'
// S16-b 대안 · 한 곳 빼기 (④): 대안 고르기 화면 위 [한 곳 빼기] 탭으로 들어와요
// 남은 장소 중 뺄 곳 하나를 고르고, 남는 시간을 다음 장소에 쓸지 정해요.
// 1. 뺄 수 있는 곳마다 "빼면 어떻게 되는지"를 서버가 미리 계산해요 (POST /live/preview)
// 2. 고른 대로 일정에 적용해요 (POST /live/apply)
// 여유: 고정 일정에 도착하고 남는 시간(분)이에요. 음수면 늦어요.
// 고정 일정(예약·공연처럼 시각이 정해진 칸)은 뺄 수 없어요.
// 꼭 갈 곳은 자물쇠가 보이고, 누르면 한 번 더 물어본 뒤 뺄 수 있어요.
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import LateBadge from '@/components/live/LateBadge.vue'
import { importance } from '@/data/places'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const BUFFER_MIN = 10 // 남는 시간을 다음 장소에 줄 때 남겨 둘 여유(분). 실제 길찾기가 추정보다 몇 분 길 수 있어서
const EXTRA_DIVIDE = 2 // 다음 장소에는 뺀 곳 머묾의 절반까지만 더 줌 (예: 60분 머물 곳을 빼면 최대 30분)
const STEP_MIN = 5

const router = useRouter()
const replan = useReplanStore()
const results = ref({}) // 칸 id → 그 칸을 뺐을 때 서버 계산 결과
const dropId = ref(null) // 고른 뺄 곳
const useExtra = ref(true) // 남는 시간을 다음 장소에 쓰기
const final = ref(null) // 고른 대로(남는 시간 포함) 계산한 결과
const isApplying = ref(false)
const askingStop = ref(null) // 꼭 갈 곳을 빼려고 누른 칸 (확인 창)

onMounted(async () => {
  await replan.load()
  if (!replan.base) return
  for (const stop of replan.stops.filter((item) => !item.fixed)) {
    try {
      results.value[stop.id] = await replan.preview(listWithout(stop.id, null))
    } catch (e) {
      showToast(e.message)
    }
  }
  dropId.value = recommendedDropId()
})

// 추천할 뺄 곳 후보: 고정 일정·꼭 갈 곳·숙소는 빼요
// 숙소 도착은 마감이 아니라 늦어지면 뒤로 밀릴 뿐이라 뺄 이유가 없어요
const isStay = (stop) => /숙소|호텔|숙박|모텔|게스트하우스/.test(stop.category || '') || /^(숙소|예약해 둔 숙소)/.test(stop.memo || '')
const droppable = computed(() => replan.stops.filter((stop) => !stop.fixed && stop.importance !== 'must' && !isStay(stop)))
const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))
const dropStop = computed(() => replan.stops.find((stop) => stop.id === dropId.value) || null)

// 처음에 골라 둘 곳: 서버 추천 대안 B(한 곳 빼기)가 뺀 곳
// 그런 곳이 없으면, 뺐을 때 여유가 가장 큰 곳
function recommendedDropId() {
  const option = replan.findOption('한 곳 빼기')
  if (option) {
    const keptIds = option.stops.map((stop) => stop.id)
    const dropped = droppable.value.find((stop) => !keptIds.includes(stop.id))
    if (dropped) return dropped.id
  }
  let best = null
  for (const stop of droppable.value) {
    const result = results.value[stop.id]
    if (result && (!best || result.slackMin > results.value[best].slackMin)) best = stop.id
  }
  return best
}

// 남는 시간을 받을 다음 장소: 뺀 곳 바로 다음 칸 (고정 일정이 아닐 때만)
const extra = computed(() => {
  if (!dropStop.value || !results.value[dropId.value]) return null
  const index = replan.stops.indexOf(dropStop.value)
  const next = replan.stops[index + 1]
  if (!next || next.fixed) return null
  const isBeforeBasis = index + 1 < replan.stops.indexOf(replan.basisStop)
  // 더 줄 시간: 뺀 곳 머묾의 절반까지 (5분 단위로 내림)
  // 기준 칸(basisStop, 늦으면 안 되는 고정 일정) 앞이면 여유를 BUFFER_MIN(10분)은 남겨요
  const half = dropStop.value.stay / EXTRA_DIVIDE
  const room = isBeforeBasis ? results.value[dropId.value].slackMin - BUFFER_MIN : half
  const minutes = Math.floor(Math.min(half, room) / STEP_MIN) * STEP_MIN
  return minutes > 0 ? { stop: next, minutes } : null
})

const extraText = computed(() => {
  if (!extra.value) return '남는 시간 없음'
  if (!useExtra.value) return '비워 둠'
  return `${extra.value.stop.name} ${toDuration(extra.value.stop.stay)} → ${toDuration(extra.value.stop.stay + extra.value.minutes)}`
})

// 서버에 보낼 목록: id 칸을 빼고, extraInfo가 있으면 다음 장소 머묾을 늘려요
function listWithout(id, extraInfo) {
  const list = []
  for (const stop of replan.stops) {
    if (stop.id === id) continue
    if (extraInfo && extraInfo.stop.id === stop.id) list.push({ id: stop.id, stay: stop.stay + extraInfo.minutes })
    else list.push({ id: stop.id })
  }
  return list
}

// 뺄 곳이나 [남는 시간 다음 곳에]가 바뀌면 적용할 일정을 다시 계산해요
watch([dropId, useExtra], async () => {
  final.value = null
  if (!dropId.value) return
  const extraInfo = useExtra.value ? extra.value : null
  if (!extraInfo) {
    final.value = results.value[dropId.value]
    return
  }
  try {
    final.value = await replan.preview(listWithout(dropId.value, extraInfo))
  } catch (e) {
    showToast(e.message)
  }
})

// 꼭 갈 곳 확인 창에서 [빼기]를 눌렀을 때
function confirmDrop() {
  dropId.value = askingStop.value.id
  askingStop.value = null
}

function resultText(id) {
  const result = results.value[id]
  if (!result) return '계산 중'
  const slack = result.slackMin < 0 ? toDuration(-result.slackMin) + ' 늦음' : '여유 ' + toDuration(result.slackMin)
  return slack
}
function resultClass(id) {
  const result = results.value[id]
  return result && result.slackMin >= 0 ? 'c-success' : 'c-warning'
}

async function apply() {
  if (!final.value || isApplying.value) return
  isApplying.value = true
  try {
    await replan.apply(final.value)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <div class="scr scr-AltDrop">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay"><LateBadge /></TopBar>

      <div v-if="!replan.base" class="scroll">
        <PageMessage v-if="replan.error" icon="i-warning" title="대안을 만들 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else is-loading title="지금 위치에서 다시 계산하는 중이에요" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <AltTabs active="drop" />

        <div class="now-block">
          <span class="me" aria-hidden="true"></span>
          <img v-if="replan.currentStop" class="thumb" :src="imageFor(replan.currentStop.category)" alt="" style="width: 40px; height: 40px" />
          <span style="flex: 1"><span class="t-strong" style="display: block">지금 · {{ replan.currentStop ? replan.currentStop.name : '이동 중' }}</span><span class="t-cap tn">{{ replan.status.now }}</span></span>
        </div>

        <p class="divlabel">남은 일정 · 뺄 곳을 골라요</p>

        <div role="radiogroup" aria-label="뺄 장소" style="display: flex; flex-direction: column; gap: 8px">
          <template v-for="stop in replan.stops" :key="stop.id">
            <div v-if="stop.fixed" class="opt is-locked">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body"><b class="t-sub">{{ stop.name }}</b><span class="t-cap tn">{{ stop.eta }} 고정</span></span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <button v-else-if="stop.importance === 'must'" type="button" role="radio" :aria-checked="dropId === stop.id" class="opt" :class="{ 'is-on': dropId === stop.id }" style="text-align: left" @click="dropId === stop.id ? (dropId = null) : (askingStop = stop)">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body">
                <span class="drop-name"><b class="t-sub">{{ stop.name }}</b><span class="imp imp--must">꼭</span></span>
                <span class="t-cap tn" :class="resultClass(stop.id)">{{ resultText(stop.id) }}</span>
              </span>
              <i class="ic" :class="dropId === stop.id ? 'i-check' : 'i-lock subtle'" aria-hidden="true"></i>
            </button>
            <button v-else type="button" role="radio" :aria-checked="dropId === stop.id" class="opt" :class="{ 'is-on': dropId === stop.id }" style="text-align: left" @click="dropId = stop.id">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body">
                <span class="drop-name"><b class="t-sub">{{ stop.name }}</b><span class="imp" :class="'imp--' + (stop.importance || 'maybe')">{{ importance[stop.importance || 'maybe'] }}</span></span>
                <span class="t-cap tn" :class="resultClass(stop.id)">{{ resultText(stop.id) }}</span>
              </span>
              <span class="radio"></span>
            </button>
          </template>
        </div>

        <button v-if="dropStop" type="button" class="opt" role="checkbox" :aria-checked="useExtra && !!extra" :disabled="!extra" style="text-align: left; box-shadow: none" @click="useExtra = !useExtra">
          <span class="check" :class="{ 'is-on': useExtra && extra }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          <span class="opt__body"><b class="t-strong">남는 시간 다음 곳에</b><span class="t-cap tn">{{ extraText }}</span></span>
        </button>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!final || isApplying" @click="apply">{{ dropStop ? dropStop.name + ' 빼고 적용' : '뺄 곳을 골라 주세요' }}</button>
      </div>
      </template>

      <ConfirmModal v-if="askingStop" :img="imageFor(askingStop.category)" :title="'꼭 갈 곳인데 뺄까요?'" :text="askingStop.name" ok-text="빼기" ok-icon="i-trash" danger @cancel="askingStop = null" @ok="confirmDrop" />
    </div>
  </div>
</template>

<style>
.scr-AltDrop .drop-name { display: flex; align-items: center; gap: 6px; }
</style>
