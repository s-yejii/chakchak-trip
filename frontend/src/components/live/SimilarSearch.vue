<script setup>
// 놓친 예약과 비슷한 근처 가게 (④ 11/12): 놓침 카드의 [비슷한 근처 가게 찾기]에서 열려요.
//   POST /live/recommend + similarTo(놓친 장소): 업종(biz_type)이 겹치는 곳, 같은 브랜드 다른 지점 먼저 (AI 안 씀, SQL 반경 검색 + 규칙)
//   결과는 부모(여행 중 화면) 지도에 핀으로 → 핀을 누르고 [담기] → 놓친 칸 자리에 넣고 바로 다음 목적지로 (POST /live/insert + replaceStopId)
//   모양·반경 버튼은 근처 찾기(NearSearch, ⑤)와 같게 맞췄어요
//   11/16 ③(예지 OK): [예약했어요]를 켜고 시각을 고르면 🔒 고정 일정 + 그 시각으로 담아요 (POST /live/insert + reservedTime)
//   11/16 ③ 요청: 기준 칩 [현위치] [다음 고정 일정 가는 길]. "가는 길"은 놓친 가게와 다음 고정 일정 사이 가운데를 중심으로,
//     두 곳이 다 들어가는 반경으로 찾아요 (예: 강남 스시도를 놓치면 동서울 가는 길의 잠실 스시이세이). API는 그대로, 넘기는 좌표만 바뀜
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { askRecommend, insertPlace } from '@/api/trips'
import { radiusOptions } from '@/data/ask'
import { showToast } from '@/utils/toast'
import { devMode } from '@/utils/devMode'
import { photoFor } from '@/utils/placePhoto'
import { distanceMeters } from '@/utils/geo'
import '@/assets/css/live-panel.css' // 지도 위 패널 생김새 (AI 채팅·근처 찾기·비슷한 가게 같이, 11/13)

const DEFAULT_RADIUS = 500 // 처음 반경: 근처 찾기와 같은 값
const ROUTE_MIN_RADIUS = 500  // 가는 길 반경: 두 곳 사이 거리의 절반, 이 범위 안으로 (서버는 100m~3km)
const ROUTE_MAX_RADIUS = 3000

const props = defineProps({
  tripId: { type: Number, required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  stop: { type: Object, required: true },     // 놓친 칸 { id, placeId, name, lat, lng }
  nextFixed: { type: Object, default: null }, // 놓친 칸 다음 고정 일정 { name, lat, lng } (없으면 기준 칩 없이 현위치만)
  startOnRoute: { type: Boolean, default: false }, // 놓침 카드에서 들어오면 "가는 길" 먼저
  pickedId: { type: String, default: null },  // 지도에서 누른 핀 (부모와 같이 씀)
})
const emit = defineEmits(['close', 'added', 'results', 'update:pickedId'])

// 가는 길: 놓친 가게 ~ 다음 고정 일정 가운데 점과 반경. 좌표가 하나라도 없으면 null (기준 칩을 안 보여 줌)
const route = computed(() => {
  const from = props.stop
  const to = props.nextFixed
  if (!to || from.lat == null || from.lng == null || to.lat == null || to.lng == null) return null
  const half = distanceMeters(from.lat, from.lng, to.lat, to.lng) / 2
  return {
    lat: (from.lat + to.lat) / 2,
    lng: (from.lng + to.lng) / 2,
    radius: Math.round(Math.min(ROUTE_MAX_RADIUS, Math.max(ROUTE_MIN_RADIUS, half))),
  }
})
const isOnRoute = ref(props.startOnRoute && !!route.value) // true = 가는 길, false = 현위치

const router = useRouter()
const radius = ref(DEFAULT_RADIUS)
const places = ref([])
const closedSkipped = ref(0)
const isLoading = ref(false)
const isAdding = ref(false)
const error = ref('')
const isReserved = ref(false) // [예약했어요]를 켰는지
const reservedTime = ref('')  // 예약 시각 "15:00"

const picked = computed(() => places.value.find((place) => place.id === props.pickedId))
// 11/17 주형: 시연 계정은 시나리오 가게(스시이세이, 전화로 15:00 예약)를 고르면 [예약했어요]를 켜고 15:00을 미리 골라 둠 (버튼을 놓쳐도 🔒 예약으로 담겨 배 팝업이 뜨게). 일반 계정은 비워 둠
const DEMO_RESERVE_NAME = '스시이세이'
const DEMO_RESERVE_TIME = '15:00'
watch(picked, (place) => {
  if (!devMode.isOn || !place || !place.name || !place.name.startsWith(DEMO_RESERVE_NAME)) return
  isReserved.value = true
  reservedTime.value = DEMO_RESERVE_TIME
}, { immediate: true })

// 결과·반경이 바뀌면 부모 지도에 핀과 원을 알려 줌 (가는 길이면 원 중심도 같이)
watch([places, radius], () => emit('results', { places: places.value, radius: radius.value, center: isOnRoute.value ? route.value : null }))

onMounted(() => (isOnRoute.value ? searchOnRoute() : search(DEFAULT_RADIUS)))

function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}

// 고른 가게 거리 한 줄: 현위치면 지금 위치에서, 가는 길이면 다음 고정 일정까지 ("동서울종합터미널 가는 길 · 2.5km")
const pickedDistText = computed(() => {
  if (!picked.value) return ''
  if (!isOnRoute.value) return '지금 위치에서 ' + distText(picked.value.distance)
  const meters = Math.round(distanceMeters(picked.value.lat, picked.value.lng, props.nextFixed.lat, props.nextFixed.lng))
  return props.nextFixed.name + ' 가는 길 · ' + distText(meters)
})

// 가는 길 1등 칸 이유 한 줄: "리뷰 2,064개 · ★4.92 · 가는 길에서 0.5km만 돌아가요"
function bestReason(place) {
  const parts = []
  if (place.reviewCount) parts.push('리뷰 ' + place.reviewCount.toLocaleString() + '개')
  if (place.rating != null) parts.push('★' + place.rating)
  if (place.detour != null) parts.push('가는 길에서 ' + distText(Math.max(0, place.detour)) + '만 돌아가요')
  return parts.join(' · ')
}

// 기준 칩: 현위치 ↔ 가는 길
function pickBasis(onRoute) {
  if (isOnRoute.value === onRoute) return
  isOnRoute.value = onRoute
  if (onRoute) searchOnRoute()
  else search(DEFAULT_RADIUS)
}

// 가는 길에서 찾기: 중심·반경을 route로
function searchOnRoute() {
  search(route.value.radius, route.value)
}

// 이 반경에서 비슷한 가게 찾기. center가 없으면 지금 위치 기준
async function search(meters, center = props.position) {
  radius.value = meters
  emit('update:pickedId', null)
  isLoading.value = true
  error.value = ''
  try {
    const res = await askRecommend(props.tripId, center.lat, center.lng, meters, null, [], props.stop.placeId, isOnRoute.value ? props.nextFixed : null)
    places.value = res.places
    closedSkipped.value = res.closedSkipped
  } catch (e) {
    places.value = []
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}

// [예약했어요] 켜기·끄기. 켤 때 기본 시각 = 지금(시연 시계) + 30분 뒤 가장 가까운 정각·30분
// 11/17 주형: 기본 시각은 비워 두고 사용자가 고르게 (예전 기본값 "지금 + 30분"이라 12:18엔 13:00이 들어가 있었어요)
function toggleReserved() {
  isReserved.value = !isReserved.value
}

// 담기: 놓친 칸 자리에 넣고 바로 다음 목적지로 (예약했으면 그 시각 고정 일정으로)
async function addPicked() {
  if (isAdding.value) return
  if (isReserved.value && !reservedTime.value) return showToast('예약 시각을 골라 주세요')
  isAdding.value = true
  try {
    const time = isReserved.value ? reservedTime.value : null
    const res = await insertPlace(props.tripId, picked.value.id, props.position.lat, props.position.lng, props.stop.id, null, time)
    emit('added', res.message || picked.value.name + '(으)로 바꿨어요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isAdding.value = false
  }
}
</script>

<template>
  <div class="addbar">
    <!-- 11/13 디자인: 근처 찾기·AI 채팅과 같은 카드 모양 (동작은 그대로, 닫기는 머리줄로) -->
    <section class="addcard lp-panel" aria-label="비슷한 근처 가게">
      <header class="lp-head">
        <span class="lp-avatar lp-avatar--similar" aria-hidden="true"><i class="ic ic--s i-food"></i></span>
        <b class="lp-head__title">{{ stop.name }}와(과) 비슷한 근처 가게</b>
        <button type="button" class="iconbtn" aria-label="비슷한 가게 찾기 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </header>

      <!-- 고른 가게 카드 -->
      <div v-if="picked" class="lp-pick">
        <div class="lp-place">
          <img class="lp-place__img" :src="photoFor(picked)" alt="" />
          <div class="lp-place__body">
            <b class="lp-place__name">{{ picked.name }}</b>
            <p class="lp-place__meta tn">
              <template v-if="picked.rating != null"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rating }} · </template>{{ picked.category || '장소' }}
            </p>
            <div v-if="picked.closeAt" class="lp-pills"><span class="lp-pill lp-pill--navy tn">{{ picked.closeAt }}까지 영업</span></div>
          </div>
          <button type="button" class="iconbtn" aria-label="목록으로" @click="emit('update:pickedId', null)"><i class="ic i-close muted" aria-hidden="true"></i></button>
        </div>
        <p class="lp-place__dist tn">{{ pickedDistText }}</p>
        <!-- 11/16 ③: 전화·앱으로 예약했으면 시각을 골라 🔒 고정 일정으로 -->
        <div class="lp-reserve">
          <button type="button" class="chip chip--sm" :class="{ 'is-on': isReserved }" :aria-pressed="isReserved" @click="toggleReserved"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약했어요</button>
          <input v-if="isReserved" v-model="reservedTime" type="time" step="600" class="lp-reserve__time tn" aria-label="예약 시각" />
          <span v-else class="lp-reserve__help">예약했으면 눌러서 시각을 골라요</span>
        </div>
        <div class="lp-actions">
          <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place?id=' + encodeURIComponent(picked.id))">자세히</button>
          <!-- 11/17 주형: 네이버 가게 페이지에서 바로 예약 (주소가 있을 때만) -->
          <a v-if="picked.naverUrl" :href="picked.naverUrl" target="_blank" rel="noopener" class="btn btn--secondary btn--md">네이버 예약</a>
          <button type="button" class="btn btn--primary btn--md" :disabled="isAdding" @click="addPicked">
            <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ isAdding ? '담는 중…' : isReserved ? (reservedTime ? reservedTime + ' 예약으로 담기' : '예약 시각을 골라 주세요') : '담기 · 다음 목적지로' }}
          </button>
        </div>
      </div>

      <!-- 반경 고르기 + 결과 안내 -->
      <template v-else>
        <!-- 기준 칩 (다음 고정 일정이 있을 때만): 현위치 / ○○ 가는 길 -->
        <div v-if="route" class="lp-chips">
          <button type="button" class="chip chip--sm" :class="{ 'is-on': !isOnRoute }" :aria-pressed="!isOnRoute" @click="pickBasis(false)">현위치</button>
          <button type="button" class="chip chip--sm" :class="{ 'is-on': isOnRoute }" :aria-pressed="isOnRoute" @click="pickBasis(true)">{{ nextFixed.name }} 가는 길</button>
        </div>
        <p v-if="isOnRoute" class="lp-radius__k">{{ stop.name }} ~ {{ nextFixed.name }} 사이에서 찾았어요</p>
        <div v-else class="lp-radius">
          <span class="lp-radius__k">현위치에서</span>
          <div class="lp-chips">
            <button v-for="r in radiusOptions" :key="r" type="button" class="chip chip--sm" :class="{ 'is-on': radius === r }" :aria-pressed="radius === r" @click="search(r)">
              {{ r < 1000 ? r + 'm' : '1km' }}
            </button>
          </div>
        </div>
        <p class="lp-status tn">
          <template v-if="isLoading"><i class="ic ic--s i-search" aria-hidden="true"></i>비슷한 가게 찾는 중…</template>
          <template v-else-if="error"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i>{{ error }}</template>
          <template v-else-if="places.length"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>{{ places.length }}곳 · 지도에서 핀을 눌러 보세요<template v-if="closedSkipped"> · 지금 문 닫은 {{ closedSkipped }}곳은 뺐어요</template></template>
          <template v-else><i class="ic ic--s i-info" aria-hidden="true"></i>이 반경엔 비슷한 가게가 없어요 · 더 넓게 찾아보세요</template>
        </p>
        <!-- 11/17 ③: 가는 길이면 이름 목록 (서버 순서 = 평점 4.9·리뷰 1000개 넘는 곳 중 덜 돌아가는 순). 누르면 지도 핀을 누른 것과 같아요 -->
        <ol v-if="isOnRoute && !isLoading && places.length" class="lp-names">
          <li v-for="(place, index) in places" :key="place.id">
            <button type="button" class="lp-names__item tn" @click="emit('update:pickedId', place.id)">
              <span class="lp-names__name"><span v-if="index === 0" class="badge badge--success">추천</span>{{ place.name }}</span>
              <span v-if="place.rating != null" class="lp-names__rate"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ place.rating }}</span>
            </button>
            <p v-if="index === 0" class="lp-names__why t-cap tn">{{ bestReason(place) }}</p>
          </li>
        </ol>
      </template>
    </section>
  </div>
</template>

<style>
.lp-reserve{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.lp-reserve__time{height:32px;padding:0 8px;border:1px solid var(--line-strong);border-radius:var(--radius-md);background:var(--surface-raised);color:inherit;font:inherit}
.lp-reserve__help{font-size:13px;color:var(--ink-muted)}
.lp-names{display:flex;flex-direction:column;gap:2px;margin:0;padding:0;list-style:none}
.lp-names__item{display:flex;justify-content:space-between;gap:8px;width:100%;padding:6px 4px;border:0;border-bottom:1px solid var(--line);background:none;color:inherit;font:inherit;text-align:left;cursor:pointer}
.lp-names__rate{display:inline-flex;align-items:center;gap:2px;white-space:nowrap}
.lp-names__name{display:inline-flex;align-items:center;gap:6px;min-width:0}
.lp-names__why{margin:2px 4px 4px;color:var(--ink-muted)}
</style>
