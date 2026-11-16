<script setup>
// 놓친 예약과 비슷한 근처 가게 (④ 11/12): 놓침 카드의 [비슷한 근처 가게 찾기]에서 열려요.
//   POST /live/recommend + similarTo(놓친 장소): 업종(biz_type)이 겹치는 곳, 같은 브랜드 다른 지점 먼저 (AI 안 씀, SQL 반경 검색 + 규칙)
//   결과는 부모(여행 중 화면) 지도에 핀으로 → 핀을 누르고 [담기] → 놓친 칸 자리에 넣고 바로 다음 목적지로 (POST /live/insert + replaceStopId)
//   모양·반경 버튼은 근처 찾기(NearSearch, ⑤)와 같게 맞췄어요
//   11/16 ③(예지 OK): [예약했어요]를 켜고 시각을 고르면 🔒 고정 일정 + 그 시각으로 담아요 (POST /live/insert + reservedTime)
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { askRecommend, insertPlace } from '@/api/trips'
import { radiusOptions } from '@/data/ask'
import { showToast } from '@/utils/toast'
import { photoFor } from '@/utils/placePhoto'
import { appNow } from '@/utils/devMode'
import { toTime } from '@/utils/time'
import '@/assets/css/live-panel.css' // 지도 위 패널 생김새 (AI 채팅·근처 찾기·비슷한 가게 같이, 11/13)

const DEFAULT_RADIUS = 500 // 처음 반경: 근처 찾기와 같은 값
const RESERVE_STEP_MIN = 30   // 예약 시각 기본값: 지금 + 30분 뒤 가장 가까운 정각·30분

const props = defineProps({
  tripId: { type: Number, required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  stop: { type: Object, required: true },     // 놓친 칸 { id, placeId, name }
  pickedId: { type: String, default: null },  // 지도에서 누른 핀 (부모와 같이 씀)
})
const emit = defineEmits(['close', 'added', 'results', 'update:pickedId'])

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

// 결과·반경이 바뀌면 부모 지도에 핀과 원을 알려 줌
watch([places, radius], () => emit('results', { places: places.value, radius: radius.value }))

onMounted(() => search(DEFAULT_RADIUS))

function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}

// 이 반경에서 비슷한 가게 찾기
async function search(meters) {
  radius.value = meters
  emit('update:pickedId', null)
  isLoading.value = true
  error.value = ''
  try {
    const res = await askRecommend(props.tripId, props.position.lat, props.position.lng, meters, null, [], props.stop.placeId)
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
function toggleReserved() {
  isReserved.value = !isReserved.value
  if (isReserved.value && !reservedTime.value) {
    const now = appNow()
    const minutes = now.getHours() * 60 + now.getMinutes() + RESERVE_STEP_MIN
    reservedTime.value = toTime(Math.ceil(minutes / RESERVE_STEP_MIN) * RESERVE_STEP_MIN)
  }
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
        <p class="lp-place__dist tn">지금 위치에서 {{ distText(picked.distance) }}</p>
        <!-- 11/16 ③: 전화·앱으로 예약했으면 시각을 골라 🔒 고정 일정으로 -->
        <div class="lp-reserve">
          <button type="button" class="chip chip--sm" :class="{ 'is-on': isReserved }" :aria-pressed="isReserved" @click="toggleReserved"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약했어요</button>
          <input v-if="isReserved" v-model="reservedTime" type="time" step="600" class="lp-reserve__time tn" aria-label="예약 시각" />
          <span v-else class="lp-reserve__help">예약했으면 눌러서 시각을 골라요</span>
        </div>
        <div class="lp-actions">
          <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place?id=' + encodeURIComponent(picked.id))">자세히</button>
          <button type="button" class="btn btn--primary btn--md" :disabled="isAdding" @click="addPicked">
            <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ isAdding ? '담는 중…' : isReserved ? reservedTime + ' 예약으로 담기' : '담기 · 다음 목적지로' }}
          </button>
        </div>
      </div>

      <!-- 반경 고르기 + 결과 안내 -->
      <template v-else>
        <div class="lp-radius">
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
      </template>
    </section>
  </div>
</template>

<style>
.lp-reserve{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.lp-reserve__time{height:32px;padding:0 8px;border:1px solid var(--line-strong);border-radius:var(--radius-md);background:var(--surface-raised);color:inherit;font:inherit}
.lp-reserve__help{font-size:13px;color:var(--ink-muted)}
</style>
