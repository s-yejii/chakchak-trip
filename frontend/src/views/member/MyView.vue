<script setup>
// S19 마이 (①): 내 정보 · 여행 스타일 · 결제 카드 · 알림 설정 · 권한
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '@/api/http'
import { getTripList } from '@/api/trips'
import TabBar from '@/components/common/TabBar.vue'
import TopBar from '@/components/common/TopBar.vue'
import VersionTap from '@/components/member/VersionTap.vue'
import { readAlarms, readStyle, saveAlarms } from '@/utils/mySettings'
import DevDot from '@/components/live/DevDot.vue'
import { turnOffDevMode } from '@/utils/devMode'
import { clearAllChats } from '@/utils/askChat'
import { showToast } from '@/utils/toast'
import { readTransports } from '@/utils/transports'
import { withoutPractice } from '@/utils/practice'

// 가짜 데이터: 내 정보 (백엔드가 붙으면 member · member_style · card 테이블)
const router = useRouter()

// 내 정보: 화면이 열리면 서버(GET /api/auth/me)에서 받아 와요
const me = ref({ name: '', id: '', area: '서울' }) // 지역은 아직 DB에 없어서 그대로
const tripStats = ref({ done: 0, planned: 0, ongoing: 0 }) // 내 여행 수 (GET /api/trips)

// onMounted: 화면이 처음 뜰 때 한 번 실행
onMounted(async () => {
  try {
    const user = await get('/auth/me')
    me.value.name = user.nickname || user.username // 닉네임이 없으면 아이디
    me.value.id = user.username
  } catch (e) {
    router.replace('/login') // 로그인 안 했으면(401) 로그인 화면으로
    return
  }
  try {
    const trips = withoutPractice(await getTripList()) // 시연 연습 복사본은 숨김
    tripStats.value = {
      done: trips.filter((trip) => trip.status === 'done').length,
      planned: trips.filter((trip) => trip.status === 'planned').length,
      ongoing: trips.filter((trip) => trip.status === 'ongoing').length,
    }
  } catch {
    // 여행 수를 못 받아도 마이 화면은 보여 줌 (0으로)
  }
})
// 내 여행 스타일 줄: 스타일 화면에서 저장한 값 (11/06, 이 브라우저에 저장)
const savedStyle = readStyle()
const PACE_NAMES = { slow: '느긋한 여행', mid: '보통 여행', fast: '빡빡한 여행' }
const styleChips = [PACE_NAMES[savedStyle.pace] || '보통 여행', ...savedStyle.likes]
const transports = readTransports() // 설정 > 이동 수단 줄에 보여 줄 값 (11/06)
// 11/16 상재: 친구 칸은 가짜 데이터뿐이라 마이 화면에서 뺐어요
const cards = ref([
  { name: '신한카드', last: '4821', main: true },
])

// 알림 켜기/끄기
// 켜고 끈 값은 이 브라우저에 저장해서 다시 들어와도 그대로 (11/06, 서버 알림 설정 API가 생기면 utils/mySettings.js만 바꿈)
const savedAlarms = readAlarms()
const alarms = ref([
  { key: 'depart', icon: 'i-walk', tile: 'sky', title: '출발 알림', on: savedAlarms.depart },
  { key: 'delay', icon: 'i-warning', tile: 'warn', title: '지연·대안 알림', on: savedAlarms.delay },
  { key: 'weather', icon: 'i-cloud', tile: 'lilac', title: '날씨 알림', on: savedAlarms.weather },
])
const departBefore = ref(savedAlarms.departBefore) // 출발 몇 분 전에 알릴지
watch([alarms, departBefore], () => {
  const values = { departBefore: departBefore.value }
  for (const alarm of alarms.value) values[alarm.key] = alarm.on
  saveAlarms(values)
}, { deep: true })

function setMainCard(index) {
  cards.value.forEach((card, i) => (card.main = i === index))
  showToast('기본 결제 카드를 바꿨어요')
}

// 로그아웃: 서버 세션을 지우고 첫 화면으로
async function logout() {
  await post('/auth/logout')
  turnOffDevMode() // 다른 사람이 이 브라우저로 로그인해도 개발자 모드가 남지 않게
  clearAllChats() // AI에게 묻기 대화도 전부 지워서 다음 사람에게 안 보이게 (④ 요청, 11/13)
  router.replace('/login')
}

</script>

<template>
  <div class="scr scr-My">
    <div class="app">
      <DevDot /><!-- 개발자 모드(시연 계정)일 때만 보이는 점: 여행 시작 전에도 시연 시계를 맞출 수 있게 (11/06) -->
      <TopBar title="마이" icon="none" />

      <div class="scroll" style="padding-top: 8px; gap: 24px; bottom: 96px">
        <section class="my-hero" aria-label="내 정보">
          <div class="my-hero__top">
            <span class="my-hero__ava">{{ me.name[0] }}</span>
            <div class="my-hero__name">
              <p>{{ me.name }}</p>
              <span class="tn">{{ me.id }}</span>
            </div>
            <RouterLink to="/my/profile" class="my-hero__edit" aria-label="정보 수정"><i class="ic ic--s i-edit" aria-hidden="true"></i></RouterLink>
          </div>
          <RouterLink to="/trips" class="my-stats" aria-label="내 여행 보기">
            <span class="my-stat"><b class="tn">{{ tripStats.done }}</b>다녀온 여행</span>
            <i aria-hidden="true"></i>
            <span class="my-stat"><b class="tn">{{ tripStats.planned }}</b>예정 여행</span>
            <i aria-hidden="true"></i>
            <span class="my-stat" :class="{ 'is-live': tripStats.ongoing }"><b class="tn">{{ tripStats.ongoing }}</b>여행 중</span>
          </RouterLink>
        </section>

        <RouterLink to="/my/style" class="my-style" aria-label="내 여행 스타일 수정">
          <span class="ico-tile ico-tile--lilac"><i class="ic i-sparkle" aria-hidden="true"></i></span>
          <span class="my-style__body">
            <b>내 여행 스타일</b>
            <span class="tn">{{ styleChips.slice(1).join(' · ') }}</span>
          </span>
          <i class="ic i-next subtle" aria-hidden="true"></i>
        </RouterLink>

        <!-- 11/06: 큰 앱 마이 화면처럼 자주 쓰는 순서로 — 내 여행 → 알림 → 결제 → 설정 → 언어 (친구 칸은 11/16에 뺌) -->
        <section class="my-sec" aria-labelledby="tr-h">
          <h2 id="tr-h" class="t-sub">내 여행</h2>
          <div class="group">
            <RouterLink to="/trips" class="row"><span class="ico-tile ico-tile--sky"><i class="ic i-calendar" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">내 여행 일정</span><span class="row__meta tn">예정 {{ tripStats.planned }} · 다녀온 {{ tripStats.done }}</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/places" class="row"><span class="ico-tile ico-tile--sky"><i class="ic i-bookmark" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">저장한 장소</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/trash" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-route" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">여행 기록</span><span class="row__meta">지운 여행 다시 살리기</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/location" class="row"><span class="ico-tile ico-tile--lilac"><i class="ic i-pin" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">내 위치 기록</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="nt-h">
          <h2 id="nt-h" class="t-sub">알림</h2>
          <div class="group">
            <template v-for="alarm in alarms" :key="alarm.key">
              <div class="row">
                <span class="ico-tile" :class="alarm.tile ? 'ico-tile--' + alarm.tile : ''"><i class="ic" :class="alarm.icon" aria-hidden="true"></i></span>
                <span class="row__body"><span class="row__title">{{ alarm.title }}</span></span>
                <button type="button" role="switch" class="toggle" :class="{ 'is-on': alarm.on }" :aria-checked="alarm.on" :aria-label="alarm.title" @click="alarm.on = !alarm.on"></button>
              </div>
              <!-- 출발 알림이 켜져 있을 때만 몇 분 전인지 골라요 -->
              <div v-if="alarm.key === 'depart' && alarm.on" class="my-before">
                <div class="chips" role="radiogroup" aria-label="출발 몇 분 전" style="flex-wrap: nowrap">
                  <button v-for="min in [5, 10, 15]" :key="min" type="button" role="radio" class="chip chip--sm tn" :class="{ 'is-on': departBefore === min }" :aria-checked="departBefore === min" @click="departBefore = min">{{ min }}분 전</button>
                </div>
              </div>
            </template>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="cd-h">
          <h2 id="cd-h" class="t-sub">결제 카드</h2>
          <div class="group">
            <div v-for="(card, i) in cards" :key="card.last" class="row">
              <span class="ico-tile ico-tile--coral"><i class="ic i-won" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ card.name }} <span class="tn muted" style="font-weight: 500">{{ card.last }}</span></span></span>
              <span v-if="card.main" class="badge badge--navy">기본</span>
              <button v-else type="button" class="btn btn--secondary btn--sm" @click="setMainCard(i)">기본으로</button>
            </div>
            <RouterLink to="/my/card" class="row">
              <span class="ico-tile ico-tile--add"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title c-navy">카드 등록</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="st-h">
          <h2 id="st-h" class="t-sub">설정</h2>
          <div class="group">
            <RouterLink to="/my/transport" class="row"><span class="ico-tile ico-tile--sky"><i class="ic i-subway" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">이동 수단</span><span class="row__meta">{{ transports.join(' · ') }}</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/permission" class="row"><span class="ico-tile ico-tile--ok"><i class="ic i-locate" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">위치 권한</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <a href="tel:15880000" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-help" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">고객센터</span></span><i class="ic i-next subtle" aria-hidden="true"></i></a>
            <!-- 11/06: 언어는 위 오른쪽 버튼 대신 맨 아래 한 줄. 한국어 화면에서는 영어로 바꾸는 줄이라 영어로 써요 -->
            <button type="button" class="row my-lang-row" lang="en" @click="showToast('English 화면은 준비 중이에요')"><span class="ico-tile ico-tile--neutral"><i class="ic i-chat" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">Language</span></span><span class="my-lang-now">English</span><i class="ic i-next subtle" aria-hidden="true"></i></button>
          </div>
        </section>

        <DevDot inline /><!-- 개발자 설정: 시연 계정(개발자 모드)만 보임 · 배속·멈춤·장면·다음 장소 (③ 11/08) -->
        <div class="my-foot">
          <button type="button" class="btn btn--ghost btn--sm" @click="logout"><i class="ic ic--s i-logout" aria-hidden="true"></i>로그아웃</button>
          <VersionTap />
        </div>
      </div>

      <TabBar active="my" />
    </div>
  </div>
</template>

<style>
.scr-My .topbar__title{padding-left:8px}
.scr-My .my-hero{display:flex;flex-direction:column;gap:18px;padding:20px;border-radius:var(--radius-xl);background:var(--navy-soft)}
.scr-My .my-hero__top{display:flex;align-items:center;gap:14px}
.scr-My .my-hero__ava{flex:none;width:60px;height:60px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:800;color:#fff;background:var(--navy)}
.scr-My .my-hero__name{flex:1;min-width:0}
.scr-My .my-hero__name p{margin:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:21px;line-height:28px;font-weight:800;letter-spacing:-.3px}
.scr-My .my-hero__name span{font-size:13px;color:var(--ink-muted)}
.scr-My .my-hero__edit{flex:none;width:44px;height:44px;display:flex;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:var(--navy);box-shadow:0 2px 8px rgba(31,58,104,.1)}
.scr-My .my-stats{display:flex;align-items:center;padding:14px 8px;border-radius:var(--radius-lg);background:#fff;text-decoration:none;color:var(--ink-muted)}
.scr-My .my-stats>i{flex:none;width:1px;height:28px;background:var(--line)}
.scr-My .my-stat{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;gap:2px;font-size:12px}
.scr-My .my-stat b{font-size:20px;line-height:26px;font-weight:800;color:var(--navy)}
.scr-My .my-stat.is-live b{color:var(--coral)}
.scr-My .my-style{margin-top:-12px;display:flex;align-items:center;gap:12px;padding:16px;border-radius:var(--radius-lg);background:var(--lilac-soft);text-decoration:none;color:var(--ink);transition:transform .1s}
.scr-My .my-style .ico-tile{background:#fff}
.scr-My .my-style:active{transform:scale(.98)}
.scr-My .my-style__body{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.scr-My .my-style__body b{font-size:16px;line-height:22px;font-weight:700}
.scr-My .my-style__body span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:13px;color:var(--lilac)}
.scr-My .my-sec{display:flex;flex-direction:column;gap:10px}
.scr-My .ico-tile--add{background:transparent;border:1.5px dashed var(--line-strong);color:var(--navy)}
.scr-My .my-before{padding:0 16px 12px 68px}
.scr-My .my-foot{display:flex;justify-content:space-between;align-items:center;padding-bottom:16px}
.scr-My .my-lang-row{width:100%;border:0;background:none;font:inherit;text-align:left;cursor:pointer}
.scr-My .my-lang-now{flex:none;font-size:14px;color:var(--ink-muted)}
</style>
