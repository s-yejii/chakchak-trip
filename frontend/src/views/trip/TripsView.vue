<script setup>
// S04-b 내 여행 (②): 지금 여행 중 · 다가오는 여행 · 지난 여행
// 서버(GET /api/trips)에서 내 여행을 읽어 status로 나눠요. 못 읽으면 오류 안내, 여행이 없으면 "여행 만들기" 카드.
import { computed, onMounted, ref } from 'vue'
import { useTripStore } from '@/stores/trip'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { deleteTrip, getArchivedTrips, getTripList } from '@/api/trips'
import { daysLeft, toShortDate, tripDetailLink } from '@/utils/tripView'
import { withoutPractice } from '@/utils/practice'
import { showToast } from '@/utils/toast'
import { showTrashToast } from '@/utils/trash'

const serverTrips = ref(null) // null: 아직 못 읽음
const error = ref('')
const archivedCount = ref(0) // 보관함 여행 수 (있을 때만 맨 아래 한 줄)
onMounted(load)
const store = useTripStore() // 새 여행 만들기를 누르면 하다 만 AI 일정 입력을 비워요

async function load() {
  error.value = ''
  try {
    serverTrips.value = withoutPractice(await getTripList()) // 시연 연습 복사본은 숨김
    archivedCount.value = (await getArchivedTrips()).length
  } catch (e) {
    error.value = e.message
  }
}
const ongoing = computed(() => (serverTrips.value || []).find((trip) => trip.status === 'ongoing') || null)
// 다가오는 여행: 끝 날짜가 안 지난 것만. 안 떠나고 날짜가 지난 여행은 지난 여행으로
const planned = computed(() => (serverTrips.value || []).filter((trip) => trip.status === 'planned' && daysLeft(trip.endDate) >= 0))
const done = computed(() => (serverTrips.value || []).filter((trip) => trip.status === 'done' || (trip.status === 'planned' && daysLeft(trip.endDate) < 0)))

// 여행 삭제 (11/19): 줄 끝 ⋯ → [삭제] → 확인 창 (홈과 같은 문구). 여행 중 카드에는 ⋯가 없어요
const menuTripId = ref(null) // ⋯ 메뉴가 열린 여행 번호
const deleteTarget = ref(null) // 삭제할지 묻는 여행 (값이 있으면 확인 창이 떠 있음)
const isDeleting = ref(false)
function toggleMenu(tripId) {
  menuTripId.value = menuTripId.value === tripId ? null : tripId
}
function askDelete(trip) {
  menuTripId.value = null
  deleteTarget.value = trip
}
// 확인 창 [삭제]: 휴지통으로 옮기고 목록을 다시 읽음. 토스트의 되돌리기로 살리면 또 다시 읽음
async function removeTrip() {
  isDeleting.value = true
  try {
    const removedId = deleteTarget.value.id
    await deleteTrip(removedId)
    showTrashToast(removedId, load)
    deleteTarget.value = null
    await load()
  } catch (e) {
    showToast(e.message)
  }
  isDeleting.value = false
}
</script>

<template>
  <div class="scr scr-Trips">
    <div class="app">
      <header class="topbar" style="padding: 0 8px 0 16px">
        <h1 class="topbar__title" style="font-size: 24px; line-height: 32px">내 여행</h1>
        <!-- ⑤ 앨범으로 (11/06, ② 이찬기 동의) -->
        <RouterLink to="/album" class="btn btn--secondary btn--sm"><i class="ic ic--s i-album" aria-hidden="true"></i>앨범</RouterLink>
      </header>

      <div class="scroll" style="bottom: 84px; gap: 20px; padding-top: 4px">
        <PageMessage v-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <PageMessage v-else-if="!serverTrips" is-loading title="내 여행을 불러오는 중이에요" />
        <EmptyTripCard v-else-if="!serverTrips.length" to="/new" />
        <template v-else>
        <section aria-labelledby="t-now" class="trips-sec">
          <h2 id="t-now" class="t-strong muted">지금 여행 중</h2>
          <PhotoCard v-if="ongoing" to="/live" img="/img/cafe.jpg" :height="132">
            <span class="photo__txt now-trip">
              <span style="display: flex; flex-direction: column"><b style="font-size: 18px; line-height: 26px">{{ ongoing.title }}</b><small class="tn">{{ toShortDate(ongoing.startDate) }} · {{ ongoing.stopCount }}곳</small></span>
              <span class="btn btn--sm" style="background: #fff; color: var(--navy)">여행 이어가기</span>
            </span>
          </PhotoCard>
          <p v-else class="t-cap">진행 중인 여행이 없어요</p>
        </section>

        <section aria-labelledby="t-next" class="trips-sec">
          <h2 id="t-next" class="t-strong muted">다가오는 여행</h2>
          <div class="group">
            <!-- 줄 = 상세로 가는 링크 + ⋯ 버튼 (링크 안에 버튼을 못 넣어서 옆에 나란히) -->
            <div v-for="trip in planned" :key="trip.id" class="row tr-row">
              <RouterLink :to="tripDetailLink(trip)" class="tr-link">
                <img class="thumb" src="/img/dessert.jpg" alt="" />
                <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</span></span>
                <span class="badge badge--navy tn">{{ daysLeft(trip.startDate) > 0 ? 'D-' + daysLeft(trip.startDate) : '오늘' }}</span>
              </RouterLink>
              <button type="button" class="tr-more" :aria-label="trip.title + ' 더 보기'" :aria-expanded="menuTripId === trip.id" @click="toggleMenu(trip.id)"><i></i><i></i><i></i></button>
              <div v-if="menuTripId === trip.id" class="tr-menu" role="menu">
                <button type="button" role="menuitem" @click="askDelete(trip)"><i class="ic ic--s i-trash" aria-hidden="true"></i>삭제</button>
              </div>
            </div>
            <RouterLink to="/new" class="row tr-new" @click="store.resetPlan()">
              <span class="thumb tr-new__ic"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">새 여행 만들기</span><span class="row__meta">AI로 짜거나 직접 골라요</span></span>
              <i class="ic i-next" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section aria-labelledby="t-past" class="trips-sec">
          <h2 id="t-past" class="t-strong muted">지난 여행 {{ done.length }}</h2>
          <div class="group">
            <div v-for="trip in done" :key="trip.id" class="row tr-row">
              <RouterLink :to="trip.status === 'done' ? '/retro?id=' + trip.id : tripDetailLink(trip)" class="tr-link">
                <img class="thumb" src="/img/euljiro.jpg" alt="" />
                <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</span></span>
                <i class="ic i-next subtle" aria-hidden="true"></i>
              </RouterLink>
              <button type="button" class="tr-more" :aria-label="trip.title + ' 더 보기'" :aria-expanded="menuTripId === trip.id" @click="toggleMenu(trip.id)"><i></i><i></i><i></i></button>
              <div v-if="menuTripId === trip.id" class="tr-menu" role="menu">
                <button type="button" role="menuitem" @click="askDelete(trip)"><i class="ic ic--s i-trash" aria-hidden="true"></i>삭제</button>
              </div>
            </div>
          </div>
          <p v-if="!done.length" class="t-cap">아직 다녀온 여행이 없어요</p>
        </section>
        <RouterLink v-if="archivedCount" to="/trips/archive" class="textlink tn" style="align-self: center">보관함 {{ archivedCount }}</RouterLink>
        </template>
      </div>

      <TabBar active="trips" />

      <!-- ⋯ 메뉴 바깥을 누르면 닫기 (투명 막) -->
      <div v-if="menuTripId !== null" class="tr-menu-dim" @click="menuTripId = null"></div>
      <!-- 삭제 확인 창: 홈(MainView)과 같은 문구·모양 -->
      <template v-if="deleteTarget">
        <div class="dim" style="z-index: 20" @click="deleteTarget = null"></div>
        <section class="tr-confirm" role="alertdialog" aria-modal="true" aria-label="여행 삭제 확인">
          <h2 class="h-title" style="margin: 0">이 여행을 삭제할까요?</h2>
          <p class="t-cap tr-confirm__name">{{ deleteTarget.title }} · 여행 기록으로 옮겨요</p>
          <div class="cta__row">
            <button type="button" class="btn btn--secondary btn--lg" @click="deleteTarget = null">취소</button>
            <button type="button" class="btn btn--danger btn--lg" :disabled="isDeleting" @click="removeTrip">{{ isDeleting ? '삭제 중' : '삭제' }}</button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style>
.scr-Trips .trips-sec { display: flex; flex-direction: column; gap: 10px; }
.scr-Trips .now-trip { flex-direction: row; align-items: flex-end; justify-content: space-between; gap: 8px; }
.scr-Trips .tr-new{background:var(--navy-soft);color:var(--navy)}
.scr-Trips .tr-new:hover{background:#DCE4F2}
.scr-Trips .tr-new .row__title{color:var(--navy);font-weight:700}
.scr-Trips .tr-new .row__meta{color:var(--navy);opacity:.75}
.scr-Trips .tr-new .ic{color:var(--navy)}
.scr-Trips .tr-new__ic{display:flex;align-items:center;justify-content:center;background:#fff;border:1px solid #C9D5EA}
.scr-Trips .tr-new__ic .ic{color:var(--navy);width:22px;height:22px}
/* 줄: 링크가 줄을 거의 다 채우고(누르면 상세), 오른쪽 끝에 ⋯ (11/19) */
.scr-Trips .tr-row { padding: 0 4px 0 0; gap: 0; }
.scr-Trips .tr-link { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12px; padding: 10px 8px 10px 16px; color: inherit; text-decoration: none; }
/* ⋯ 버튼·메뉴·확인 창: 홈(MainView .home-more·.home-menu·.home-confirm)과 같은 모양 */
.scr-Trips .tr-more { flex: none; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; gap: 3px; border: 0; border-radius: 50%; background: none; cursor: pointer; }
.scr-Trips .tr-more i { width: 4px; height: 4px; border-radius: 50%; background: var(--ink-muted); }
.scr-Trips .tr-more:hover { background: var(--surface-sunken); }
.scr-Trips .tr-menu { position: absolute; top: 50%; right: 48px; transform: translateY(-50%); z-index: 12; display: flex; flex-direction: column; min-width: 120px; padding: 6px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-sheet); } /* ⋯ 왼쪽에 (.group이 overflow:hidden이라 줄 밖으로 나가면 잘려서 줄 안에) */
.scr-Trips .tr-menu button { display: flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 10px; border: 0; border-radius: var(--radius-sm); background: none; font: inherit; font-size: 14px; font-weight: 600; color: var(--danger); cursor: pointer; }
.scr-Trips .tr-menu button:hover { background: var(--surface-sunken); }
.scr-Trips .tr-menu-dim { position: absolute; inset: 0; z-index: 4; }
.scr-Trips .tr-confirm { position: absolute; left: 0; right: 0; bottom: 0; z-index: 21; display: flex; flex-direction: column; gap: 8px; padding: 24px 16px; border-radius: var(--radius-xl) var(--radius-xl) 0 0; background: #fff; }
.scr-Trips .tr-confirm__name { margin: 0 0 12px; }
.scr-Trips .tr-confirm .btn { flex: 1; }
</style>
