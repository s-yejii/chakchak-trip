<script setup>
// ③ 한강버스로도 가요 (11/16): 예약까지 시간이 많이 뜰 때 길 안내 아래 한 줄 카드
// 여유가 많으면 내린 선착장 근처에서 쉬었다 가는 문구 (restMin, 예약 15분 전 도착 목표)
// GET /api/plans/hangang-bus → 권할 게 없으면 204(null)라 아무것도 안 보여요. 실패해도 조용히 숨김 (길 안내가 본 기능)
// 규칙(서버): 예약 10분 전 도착 · 바로 가면 30분 넘게 기다림 · 배에서 내리기 전 비 없음 · 탈 배가 시간표에 있음
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '@/api/http'
import { isFakeGps, sendCommand } from '@/utils/devMode'
import { toDuration, toMinutes, toTime } from '@/utils/time'
import { boatChecking, boatPending } from '@/utils/demoRoute'

const REST_SHOW_MIN = 10 // 쉬는 시간이 이 이상이면 "둘러보고 가요" 문구
const BOARD_EARLY_MIN = 5 // 배 떠나기 이만큼 전에 선착장 도착 (서버 규칙과 같음)

const props = defineProps({
  fromLat: { type: Number, required: true },
  fromLng: { type: Number, required: true },
  toLat: { type: Number, required: true },
  toLng: { type: Number, required: true },
  arriveBy: { type: String, required: true }, // 예약 시각 "15:00"
  beforeLeave: { type: Boolean, default: false }, // 11/17: 여행 중 화면에서 출발 전에 먼저 묻는 모양 ("딱 맞게 도착해요" + [그냥 지하철])
  popup: { type: Boolean, default: false }, // 11/17 주형: 화면 가운데 큰 팝업으로 (고르기 전엔 뒤 화면을 못 누름)
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
  setPending(false)
  emit('skip') // 고른 뒤엔 팝업·카드 닫기 (여행 중 화면이 이 칸을 다시 묻지 않게)
  if (isFakeGps()) rideDemo()
  else router.push('/live/route')
}

// 출발 전 모양이면: 카드가 떠 있는 동안 시연 [다음 장소로]를 막음 (고르면 풀림)
function setPending(value) {
  if (props.beforeLeave) boatPending.value = value
}

async function load() {
  // 11/17: 묻는 동안은 막지 않음 (팔각정처럼 권할 배가 없는 곳에서 [다음 장소로]가 몇 초 막혔어요). 배가 있다는 대답 뒤에만 막아요
  if (props.beforeLeave) boatChecking.value = true
  const query = `fromLat=${props.fromLat}&fromLng=${props.fromLng}&toLat=${props.toLat}&toLng=${props.toLng}&arriveBy=${props.arriveBy}`
  try {
    option.value = await get('/plans/hangang-bus?' + query)
  } catch {
    option.value = null
  }
  setPending(!!option.value)
  if (props.beforeLeave) boatChecking.value = false
}

onMounted(load)
onUnmounted(() => {
  setPending(false)
  if (props.beforeLeave) boatChecking.value = false
})

// [그냥 지하철]: 카드 닫고 출발 막기 풀기
function skip() {
  setPending(false)
  emit('skip')
}
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
  <div v-if="option" :class="popup ? 'hb-pop' : 'hb-inline'" :role="popup ? 'alertdialog' : null" :aria-modal="popup ? 'true' : null">
  <section class="hb-option" :class="{ 'hb-option--pop': popup }" aria-label="한강버스로 가는 방법">
    <b class="hb-option__title"><span aria-hidden="true">⛴</span> {{ beforeLeave ? '한강버스로 가면 딱 맞게 도착해요' : '한강버스로 가요' }}</b>
    <!-- 11/20 주형: 핵심(나갈 시각·도착·예약 몇 분 전·남는 시간)은 색으로 바로 보이게 -->
    <span class="tn"><b class="hb-key">{{ leaveTime(option) }} 출발</b> · 배 {{ option.boardAt }} → {{ option.alightPier }} {{ option.alightAt }}</span>
    <span class="tn"><b class="hb-key">{{ arriveTime(option) }} 도착</b> <span class="hb-good">예약 {{ earlyMin(option) }}분 전</span></span>
    <!-- 11/20 주형: 배 타기 전에 한강버스가 뭐가 좋은지 한 줄 (요금은 우리 DB에 없어서 안 적어요) -->
    <p v-if="beforeLeave" class="t-cap hb-option__intro">서울시 강 위 대중교통이에요. 지하철보다 느려도 앉아서 한강 보며 남는 시간을 써요.</p>
    <div v-if="beforeLeave" class="hb-option__actions">
      <button type="button" class="btn btn--primary" :class="popup ? 'btn--lg btn--block' : 'btn--sm'" @click="ride">⛴ 배 타고 갈게요</button>
      <button type="button" class="btn btn--secondary" :class="popup ? 'btn--lg btn--block' : 'btn--sm'" @click="skip">대중교통 타고 가기</button>
    </div>
    <button v-else-if="isFakeGps()" type="button" class="btn btn--secondary btn--sm" @click="rideDemo">⛴ 배 타고 갈게요</button>
    <p class="t-cap tn hb-option__sub">대중교통은 {{ option.directArriveAt }} 도착, 시간이 <b class="hb-wait">{{ toDuration(option.directWaitMin) }}</b> 남아요</p>
  </section>
  </div>
</template>

<style>
.hb-option{display:flex;flex-direction:column;gap:4px;padding:10px 12px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised)}
.hb-option__title{font-weight:700}
.hb-key{font-weight:700;color:var(--navy)}
.hb-good{display:inline-block;margin-left:4px;padding:0 8px;border-radius:999px;background:var(--success-soft);color:var(--success);font-weight:700;font-size:13px;line-height:22px}
.hb-wait{font-weight:700;color:var(--ink)} /* 다른 안(대중교통)은 회색 글에 차이 숫자만 굵게 */
.hb-option__sub{margin:0}
.hb-option__intro{margin:2px 0 0;color:var(--ink-muted)}
.hb-option__actions{display:flex;gap:8px}
.hb-option__actions .btn{flex:1}
.hb-inline{display:contents}
/* 11/17 팝업: 놓침 카드(LostFixedCard)와 같은 모양 */
.hb-pop{position:absolute;inset:0;z-index:58;display:grid;place-items:center;padding:24px;background:rgba(15,20,35,.45)}
.hb-option--pop{width:100%;max-width:320px;gap:8px;padding:22px 18px 18px;border:0;border-radius:20px;box-shadow:0 16px 40px -8px rgba(24,33,58,.45);text-align:center}
.hb-option--pop .hb-option__title{font-size:18px;line-height:26px}
.hb-option--pop .hb-option__actions{flex-direction:column;margin-top:8px}
</style>
