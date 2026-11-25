<script setup>
import { LAST_STEP_ID, planStepIndex } from '@/utils/planSteps'
// S08-c 한 곳 빼고 다시 만들기 (③)
// 11/13: 예전엔 가짜 장소(천호 쭈꾸미골목 등)를 보여 줬어요. 이제 진짜 "조건 바꿔 다시 만들기"로 보내요:
//   여행 정보 화면(/plan/info)으로 돌아가요. 고른 값은 그대로 남아 있어서 고쳐서 다시 만들면 돼요
// 11/14 디자인 검토: 1단계(링크)로 가면 10단계를 다시 넘겨야 해서, 마지막 단계(만들기 버튼 있는 곳)로 보내요
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTripStore } from '@/stores/trip'
import { showToast } from '@/utils/toast'

const LAST_STEP = planStepIndex(LAST_STEP_ID) // 여행 정보 마지막 "예약한 곳이 있나요?" (11/20: 번호 대신 이름으로, utils/planSteps.js)

const router = useRouter()
const store = useTripStore()

onMounted(() => {
  store.planStep = LAST_STEP
  showToast('고른 조건 그대로예요 · 뒤로 가서 바꿀 것만 고쳐 주세요')
  router.replace('/plan/info')
})
</script>

<template>
  <div class="scr scr-GenTrim"><div class="app"></div></div>
</template>
