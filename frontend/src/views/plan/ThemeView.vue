<script setup>
import { planStepIndex } from '@/utils/planSteps'
// S04-d 테마 코스 (③): 주소 /theme?id=ikseon (data/themes.js의 id)
// 11/13: "준비 중" 대신, 누르면 그 테마의 지역·종류를 채워서 AI 일정 만들기(날짜 고르기 단계)로 바로 보내요
//   예전 테마 id(전참시 등)나 id 없이 오면 테마 목록만 보여 줘요
// 11/14 디자인 검토: 목록은 동그라미 3개만 있으면 화면이 거의 비어서 카드(사진·한 줄·동네·들어가는 곳)로 보여 줘요
import { computed, ref, watch } from 'vue'
import { getPlaceDetail } from '@/api/trips'
import { imageFor } from '@/utils/mapView'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { findTheme, themeList } from '@/data/themes'
import { useTripStore } from '@/stores/trip'

const WHEN_STEP = planStepIndex('when') // 여행 정보 단계 중 "언제 가요?" (11/20: 번호 대신 이름으로, utils/planSteps.js)

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const theme = computed(() => findTheme(route.query.id))

// 이런 곳을 가요 (11/20 피드백): 테마에 적어 둔 실제 가게·숙소를 장소 상세에서 읽어 사진(없으면 종류 아이콘)과 이름으로
const spots = ref([])
watch(theme, async (picked) => {
  spots.value = []
  if (!picked || !picked.placeIds) return
  const ids = picked.stayId ? [...picked.placeIds, picked.stayId] : picked.placeIds
  const list = []
  for (const id of ids) {
    try {
      const place = await getPlaceDetail(id)
      list.push({ id, name: place.name, imageUrl: place.imageUrl, category: place.category, isStay: id === picked.stayId })
    } catch {
      // 못 읽은 곳은 빼고 보여 줘요
    }
  }
  spots.value = list
}, { immediate: true })

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
          <div v-if="spots.length" class="theme-spots" aria-label="이런 곳을 가요">
            <b class="t-sub">이런 곳을 가요</b>
            <div class="theme-spots__row">
              <RouterLink v-for="spot in spots" :key="spot.id" :to="'/place?id=' + encodeURIComponent(spot.id)" class="theme-spot">
                <img :src="spot.imageUrl || imageFor(spot.category)" alt="" loading="lazy" :class="{ 'is-icon': !spot.imageUrl }" />
                <span class="theme-spot__name">{{ spot.name }}</span>
                <span v-if="spot.isStay" class="t-cap">숙소</span>
              </RouterLink>
            </div>
          </div>
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
.theme-spots { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.theme-spots__row { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 4px; }
.theme-spot { flex: none; width: 104px; display: flex; flex-direction: column; gap: 2px; text-decoration: none; color: var(--ink); }
.theme-spot img { width: 104px; height: 78px; border-radius: var(--radius-md); object-fit: cover; background: var(--surface-sunken); }
.theme-spot img.is-icon { object-fit: contain; padding: 14px; box-sizing: border-box; }
.theme-spot__name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
