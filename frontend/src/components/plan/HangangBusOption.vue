<script setup>
// ③ 한강버스로도 가요 (11/16): 예약까지 시간이 많이 뜰 때 길 안내 아래 한 줄 카드
// 여유가 많으면 내린 선착장 근처에서 쉬었다 가는 문구 (restMin, 예약 15분 전 도착 목표)
// GET /api/plans/hangang-bus → 권할 게 없으면 204(null)라 아무것도 안 보여요. 실패해도 조용히 숨김 (길 안내가 본 기능)
// 규칙(서버): 예약 10분 전 도착 · 바로 가면 30분 넘게 기다림 · 배에서 내리기 전 비 없음 · 탈 배가 시간표에 있음
import { onMounted, ref, watch } from 'vue'
import { get } from '@/api/http'
import { toDuration, toMinutes } from '@/utils/time'

const REST_SHOW_MIN = 10 // 쉬는 시간이 이 이상이면 "둘러보고 가요" 문구

const props = defineProps({
  fromLat: { type: Number, required: true },
  fromLng: { type: Number, required: true },
  toLat: { type: Number, required: true },
  toLng: { type: Number, required: true },
  arriveBy: { type: String, required: true }, // 예약 시각 "15:00"
})

const option = ref(null)

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

// 여유가 10분 넘게 남으면 "한강공원에서 15분 둘러보고 14:18 버스 3317 · 14:43 도착 (예약 17분 전)", 아니면 바로 가는 문구
function restText(item) {
  if (item.restMin < REST_SHOW_MIN) {
    return afterAlightText(item) + ' · ' + item.doorArriveAt + ' 도착 · 여유 ' + item.slackMin + '분'
  }
  const leave = item.afterAlightMode === 'walk' ? '출발' : item.busNo ? '버스 ' + item.busNo : '버스·지하철'
  const early = toMinutes(item.arriveBy) - toMinutes(item.relaxedArriveAt)
  return '한강공원에서 ' + item.restMin + '분 둘러보고 ' + item.leaveAt + ' ' + leave + ' · ' + item.relaxedArriveAt + ' 도착 (예약 ' + early + '분 전)'
}

// 내려서 가는 방법 한 마디: 버스 번호가 있으면 "버스 3317", 걷기면 "걸어서 8분"
function afterAlightText(item) {
  if (item.afterAlightMode === 'walk') return '걸어서 ' + item.afterAlightMin + '분'
  return item.busNo ? '버스 ' + item.busNo : '대중교통 ' + item.afterAlightMin + '분'
}
</script>

<template>
  <section v-if="option" class="hb-option" aria-label="한강버스로 가는 방법">
    <p class="hb-option__main">
      <span aria-hidden="true">⛴</span>
      <b>한강버스로도 가요</b>
      <span class="tn">{{ option.boardPier }} {{ option.boardAt }} → {{ option.alightPier }} {{ option.alightAt }} · {{ restText(option) }}</span>
    </p>
    <p class="t-cap tn hb-option__sub">
      지하철·버스로 바로 가면 {{ option.directArriveAt }} 도착, 예약 전 {{ toDuration(option.directWaitMin) }} 기다려요 · 한강버스도 교통카드로 타요
    </p>
  </section>
</template>

<style>
.hb-option{display:flex;flex-direction:column;gap:4px;padding:10px 12px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised)}
.hb-option__main{margin:0;display:flex;flex-wrap:wrap;gap:4px 6px;align-items:baseline}
.hb-option__sub{margin:0}
</style>
