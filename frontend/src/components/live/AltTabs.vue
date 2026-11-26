<script setup>
import { toDuration } from '@/utils/time'
// 대안 3가지 탭: 시간 줄이기 / 한 곳 빼기 / 장소 바꾸기 (세 화면이 같이 써요)
// 탭 아래 작은 글씨: 서버 추천 대안(POST /live/alternatives)으로 바꾸면 남는 여유
//   서버가 그 대안을 못 만들었으면 "직접 고르기"
//   뺄 수 있는 곳(고정 칸·꼭 갈 곳이 아닌 칸)이 하나도 없으면 "모두 꼭"
// active: 지금 화면의 탭 (trim | drop | swap)
import { useReplanStore } from '@/stores/replan'

defineProps({
  active: { type: String, required: true },
})

const replan = useReplanStore()

// short: 서버 대안을 찾을 때 쓰는 이름 (대안 A 시간 단축, B 한 곳 빼기, C 장소 변경)
const TABS = [
  { key: 'trim', to: '/live/alt', label: '시간 줄이기', short: '시간 단축' },
  { key: 'drop', to: '/live/alt/drop', label: '한 곳 빼기', short: '한 곳 빼기' },
  { key: 'swap', to: '/live/alt/swap', label: '장소 바꾸기', short: '장소 변경' },
]

function slackText(tab) {
  const option = replan.findOption(tab.short)
  if (!option) return replan.stops.some((stop) => !stop.fixed && stop.importance !== 'must') ? '직접 고르기' : '모두 꼭'
  return option.slackMin < 0 ? toDuration(-option.slackMin) + ' 늦음' : '여유 ' + toDuration(option.slackMin)
}
</script>

<template>
  <nav class="alt-tabs" aria-label="대안 종류">
    <RouterLink
      v-for="tab in TABS"
      :key="tab.key"
      :to="tab.to"
      replace
      :class="{ 'is-on': tab.key === active }"
      :aria-current="tab.key === active ? 'page' : null"
    >
      <b>{{ tab.label }}</b><small>{{ slackText(tab) }}</small>
    </RouterLink>
  </nav>
</template>
