<script setup>
// 예약 늦음 빠른 대안 (③): 지연 감지 화면(/live/delay)의 [대안 N개 보기]로 와요 (지하철·택시 대안이 있을 때만)
//   예약(회식·KTX)에 늦을 때 서버가 계산한 "지하철로 바로 가기 / 택시로 바로 가기"와
//   가게에 전화하기를 보여 줘요 (QuickAltCard)
//   ④ 대안 고르기(/live/alt: 시간 줄이기·한 곳 빼기·장소 바꾸기)와는 다른 화면이에요
//   빨리 가는 방법이 없으면(QuickAltCard의 empty) 빈 화면 대신 지연 감지 화면으로 돌려보내요
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import LateBadge from '@/components/live/LateBadge.vue'
import QuickAltCard from '@/components/live/QuickAltCard.vue'
import { useReplanStore } from '@/stores/replan'

const replan = useReplanStore()
const router = useRouter()

onMounted(() => replan.load())
</script>

<template>
  <div class="scr scr-Alternatives">
    <div class="app">
      <TopBar title="대안 고르기" back="/live"><LateBadge /></TopBar>

      <div v-if="!replan.base" class="scroll">
        <PageMessage v-if="replan.error" icon="i-warning" title="대안을 만들 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else is-loading title="지금 위치에서 다시 계산하는 중이에요" />
      </div>

      <div v-else class="scroll" style="padding-top: 4px; gap: 12px">
        <QuickAltCard @empty="router.replace('/live/delay')" />
      </div>
    </div>
  </div>
</template>
