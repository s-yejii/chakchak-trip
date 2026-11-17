<script setup>
// ③ 한강버스로도 가요 (11/16): 예약까지 시간이 많이 뜰 때 길 안내 아래 한 줄 카드
// 여유가 많으면 내린 선착장 근처에서 쉬었다 가는 문구 (restMin, 예약 15분 전 도착 목표)
// GET /api/plans/hangang-bus → 권할 게 없으면 204(null)라 아무것도 안 보여요. 실패해도 조용히 숨김 (길 안내가 본 기능)
// 규칙(서버): 예약 10분 전 도착 · 바로 가면 30분 넘게 기다림 · 배에서 내리기 전 비 없음 · 탈 배가 시간표에 있음
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '@/api/http'
import { isFakeGps, sendCommand } from '@/utils/devMode'
import { toDuration, toMinutes, toTime } from '@/utils/time'

const REST_SHOW_MIN = 10 // 쉬는 시간이 이 이상이면 "둘러보고 가요" 문구
const BOARD_EARLY_MIN = 5 // 배 떠나기 이만큼 전에 선착장 도착 (서버 규칙과 같음)

const props = defineProps({
  fromLat: { type: Number, required: true },
  fromLng: { type: Number, required: true },
  toLat: { type: Number, required: true },
  toLng: { type: Number, required: true },
  arriveBy: { type: String, required: true }, // 예약 시각 "15:00"
  beforeLeave: { type: Boolean, default: false }, // 11/17: 여행 중 화면에서 출발 전에 먼저 묻는 모양 ("딱 맞게 도착해요" + [그냥 지하철])
})

const option = ref(null)
const emit = defineEmits(['skip']) // [그냥 지하철]: 이 카드 닫기 (여행 중 화면)
const router = useRouter()

// 시연 계정(가짜 GPS)만: [배 타고 갈게요] → 여행 중 화면에서 숙소 → 선착장 → 배 → 버스로 움직여요 (DemoDrive). 일반 계정은 버튼 없음
function rideDemo() {
  sendCommand({ type: 'hangang', option: option.value })
  router.push('/live')
}

// [배 타고 갈게요]: 시연 계정은 가짜 GPS로 배 타고 이동, 일반 계정은 길 안내로 (배·선착장 길 보기)
function ride() {
  if (isFakeGps()) rideDemo()
  else router.push('/live/route')
}

async function load() {
  const query = `fromLat=${props.fromLat}&fromLng=${props.fromLng}&toLat=${props.toLat}&toLng=${props.toLng}&arriveBy=${props.arriveBy}`
  try {
    option.value = await get('/plans/hangang-bus?' + query)
  } catch {
    option.value = null
  }
}

onMounted(load)
// 목적지·예약이 바뀔 때만 다시 물어봄 (걸을 때마다 바뀌는 위치로는 안 부름)
watch(() => [props.toLat, props.toLng, props.arriveBy], load)

// 11/17 주형: 카드 글은 꼭 필요한 것만 (출발 시각 · 배 · 도착 · 예약 몇 분 전 · 지하철과 비교 한 줄)
// 몇 시에 나가야 하는지: 배 시각 − 5분 − 선착장까지 이동
function leaveTime(item) {
  return toTime(toMinutes(item.boardAt) - BOARD_EARLY_MIN - item.toBoardMin)
}

// 도착 시각: 여유가 많으면 선착장 근처에서 쉬었다 가는 시각(예약 15분 전쯤), 아니면 바로 가는 시각
function arriveTime(item) {
  return item.restMin >= REST_SHOW_MIN ? item.relaxedArriveAt : item.doorArriveAt
}

// 예약 몇 분 전 도착
function earlyMin(item) {
  return toMinutes(item.arriveBy) - toMinutes(arriveTime(item))
}
</script>

<template>
  <section v-if="option" class="hb-option" aria-label="한강버스로 가는 방법">
    <b class="hb-option__title"><span aria-hidden="true">⛴</span> {{ beforeLeave ? '한강버스로 가면 딱 맞게 도착해요' : '한강버스로 가요' }}</b>
    <span class="tn">{{ leaveTime(option) }} 출발 · 배 {{ option.boardAt }} → {{ option.alightPier }} {{ option.alightAt }}</span>
    <span class="tn">{{ arriveTime(option) }} 도착 · 예약 {{ earlyMin(option) }}분 전</span>
    <div v-if="beforeLeave" class="hb-option__actions">
      <button type="button" class="btn btn--primary btn--sm" @click="ride">⛴ 배 타고 갈게요</button>
      <button type="button" class="btn btn--secondary btn--sm" @click="emit('skip')">그냥 지하철</button>
    </div>
    <button v-else-if="isFakeGps()" type="button" class="btn btn--secondary btn--sm" @click="rideDemo">⛴ 배 타고 갈게요</button>
    <p class="t-cap tn hb-option__sub">지하철은 {{ option.directArriveAt }} 도착, {{ toDuration(option.directWaitMin) }} 기다려요</p>
  </section>
</template>

<style>
.hb-option{display:flex;flex-direction:column;gap:4px;padding:10px 12px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised)}
.hb-option__title{font-weight:700}
.hb-option__sub{margin:0}
.hb-option__actions{display:flex;gap:8px}
.hb-option__actions .btn{flex:1}
</style>
