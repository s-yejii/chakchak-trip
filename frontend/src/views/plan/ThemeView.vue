<script setup>
// S04-d 테마 코스 (③): 주소 /theme?id=ikseon (data/themes.js의 id)
// 11/13: "준비 중" 대신, 누르면 그 테마의 지역·종류를 채워서 AI 일정 만들기(날짜 고르기 단계)로 바로 보내요
//   예전 테마 id(전참시 등)나 id 없이 오면 테마 목록만 보여 줘요
// 11/14 디자인 검토: 목록은 동그라미 3개만 있으면 화면이 거의 비어서 카드(사진·한 줄·동네·들어가는 곳)로 보여 줘요
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { findTheme, themeList } from '@/data/themes'
import { useTripStore } from '@/stores/trip'

const WHEN_STEP = 3 // 여행 정보 11단계 중 "언제 가요?" (PlanAiView STEPS 순서)

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const theme = computed(() => findTheme(route.query.id))

// 이 테마로 시작: 여행 정보를 비우고 지역·종류·분위기를 채운 뒤 날짜 고르기로
function startWithTheme() {
  const picked = theme.value
  store.resetPlan()
  store.planArea = { ...picked.area }
  store.planKinds = [...picked.kinds]
  store.planTags = [...picked.tags]
  store.planStep = WHEN_STEP
  router.push('/plan/info')
}
</script>

<template>
  <div class="scr scr-Theme">
    <div class="app">
      <TopBar title="테마로 떠나기" back="/" />
      <div class="scroll" style="padding-top: 8px; gap: 20px; bottom: 24px">
        <section v-if="theme" class="theme-start" aria-labelledby="theme-start-title">
          <img class="theme-start__photo" :src="theme.img" alt="" />
          <h1 id="theme-start-title" class="h-display" style="margin: 0">{{ theme.title }}</h1>
          <p class="theme-start__area t-cap"><i class="ic ic--s i-pin" aria-hidden="true"></i>{{ theme.area.fullName }} · {{ theme.kindText }}</p>
          <button type="button" class="btn btn--primary btn--lg" style="width: 100%" @click="startWithTheme">이 코스로 AI 일정 만들기</button>
          <p class="t-cap muted" style="margin: 0">날짜만 고르면 영업시간·이동 거리에 맞춰 짜 드려요</p>
        </section>
        <ThemeCircles v-if="theme" title="다른 테마 둘러보기" :except-id="theme.id" :key="theme.id" />
        <section v-else class="theme-list" aria-labelledby="theme-list-title">
          <h1 id="theme-list-title" class="h-title" style="margin: 0">테마 고르기</h1>
          <RouterLink v-for="item in themeList" :key="item.id" :to="'/theme?id=' + item.id" class="theme-card">
            <img class="theme-card__photo" :src="item.img" alt="" />
            <span class="theme-card__body">
              <b class="t-strong">{{ item.title }}</b>
              <span class="t-cap">{{ item.tagline }}</span>
              <span class="t-cap muted"><i class="ic ic--s i-pin" aria-hidden="true"></i>{{ item.area.name }} · {{ item.kindText }}</span>
            </span>
            <i class="ic ic--s i-next subtle" aria-hidden="true"></i>
          </RouterLink>
        </section>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Theme .theme-start { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; }
.scr-Theme .theme-start__photo { width: 112px; height: 112px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); margin-bottom: 4px; }
.scr-Theme .theme-start__area { display: inline-flex; align-items: center; gap: 4px; margin: 0; }
.scr-Theme .theme-list { display: flex; flex-direction: column; gap: 12px; }
.scr-Theme .theme-card { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface-raised); text-decoration: none; color: var(--ink); }
.scr-Theme .theme-card:hover { border-color: var(--navy); }
.scr-Theme .theme-card__photo { width: 72px; height: 72px; flex: none; border-radius: 12px; object-fit: cover; background: var(--surface-sunken); }
.scr-Theme .theme-card__body { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 0; }
.scr-Theme .theme-card__body .muted { display: inline-flex; align-items: center; gap: 4px; }
</style>
