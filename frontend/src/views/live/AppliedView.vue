<script setup>
// S17 바뀐 일정 (④): 대안을 적용하면 이 화면으로 와요
// 무엇이 바뀌었는지 색으로 보여 줘요. 적용하고 5분 안에는 [되돌리기]로 원래 일정에 돌아갈 수 있어요.
//   비교: 방금 적용한 결과(POST /live/apply 응답)와 적용 직전 원래 칸
//   위 배너: 적용 뒤 다시 계산한 지금 상황 (POST /live/status)
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { getLiveStatus, getTrip, getTripPhotos } from '@/api/trips'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { shareTrip } from '@/utils/tripShare'

const UNDO_CHECK_MS = 15000 // 15초마다 되돌리기 버튼을 숨길지(5분 지났는지) 다시 봐요

const router = useRouter()
const replan = useReplanStore()
const after = ref(null) // 적용 뒤 지금 상황
const isUndoing = ref(false)
const canUndo = ref(replan.canUndo())
const photos = ref({}) // 칸 이름 → 장소 사진 (여행 중 화면과 같은 사진)
let undoTimer = null

onMounted(async () => {
  undoTimer = setInterval(() => (canUndo.value = replan.canUndo()), UNDO_CHECK_MS)
  if (!replan.applied) return
  loadPhotos()
  try {
    after.value = await getLiveStatus(replan.tripId, replan.position.lat, replan.position.lng)
  } catch (e) {
    showToast(e.message)
  }
})
onUnmounted(() => clearInterval(undoTimer))

// 장소 사진 받기: 여행 중 화면과 같은 사진을 쓰려고요 (종류별 기본 그림만 쓰면 달라 보여요)
//   여행 중 화면처럼 POST /places/photos/trip을 불러요
//   새로 넣은 칸까지 들어 있게, 적용한 뒤 여행을 다시 읽어서 보내요
async function loadPhotos() {
  try {
    const trip = await getTrip(replan.tripId)
    photos.value = await getTripPhotos(trip.stops)
  } catch (e) {
    photos.value = {} // 사진을 못 받으면 기본 그림
  }
}
// 칸 사진: 받은 사진이 없으면 종류별 기본 그림
function photoOf(stop) {
  return photos.value[stop.name] || imageFor(stop.category)
}

// 적용 전 칸 (id → 칸)
function beforeOf(id) {
  return (replan.before || []).find((stop) => stop.id === id) || null
}
// 적용한 계산(replan.plan)에서 이 칸까지 오는 이동 시간(분)
function moveOf(row) {
  if (!replan.plan) return null
  const found = replan.plan.stops.find((stop) => (row.id && stop.id === row.id) || (!stop.id && stop.name === row.name))
  return found ? found.moveMin : null
}

// 줄 사이 이동 표시: 적용 뒤 다시 계산한 이동(지금 → 그 칸)의 분과 수단 아이콘
//   대안 카드·여행 중 화면과 같은 숫자예요. 없으면 적용한 계산의 이동 분(걷기 아이콘)
const MODE_ICON = { walk: 'i-walk', transit: 'i-subway', car: 'i-car' }
function legOf(row) {
  const now = after.value && after.value.stops ? after.value.stops.find((stop) => stop.id === row.id) : null
  if (now && now.moveMin != null) return { min: now.moveMin, icon: MODE_ICON[now.moveMode] || 'i-walk' }
  const min = moveOf(row)
  return min == null ? null : { min, icon: 'i-walk' }
}
// 역(KTX·기차)은 음식 사진 대신 기차 아이콘
const isStation = (category) => /역|KTX|기차/.test(category || '')

// 일정 한 줄의 모양: 오른쪽 배지(줄임·늘림·그대로 등), 아래 작은 글자, 색
function toRow(row) {
  const old = beforeOf(row.id)
  const isCurrent = after.value && after.value.currentStopId === row.id
  if (row.tag === 'done') return { ...row, cls: 'is-done', badge: null, meta: isCurrent ? '지금 · 여기서 출발' : '다녀옴', isCurrent }
  if (row.tag === 'added') return { ...row, cls: 'is-changed', badge: { text: '새로 넣음', cls: 'badge--danger' }, meta: toDuration(row.stay) }
  if (old && old.fixed) {
    const fixed = after.value && after.value.fixed
    const meta = fixed ? `${fixed.eta} 도착 · ` + (fixed.slackMin < 0 ? toDuration(-fixed.slackMin) + ' 늦음' : toDuration(fixed.slackMin) + ' 여유') : '계획 시각 그대로'
    if (row.note) return { ...row, cls: 'is-fixed is-changed', isFixed: true, badge: { text: '바뀜', cls: 'badge--danger' }, meta: row.note + ' · ' + meta } // 예: 택시로 바로 가기
    return { ...row, cls: 'is-fixed', isFixed: true, badge: { text: '예약 그대로', cls: 'badge--navy' }, meta }
  }
  if (old && old.stay !== row.stay) {
    return { ...row, cls: 'is-changed', badge: { text: row.stay < old.stay ? '줄임' : '늘림', cls: 'badge--danger' }, was: toDuration(old.stay), now: toDuration(row.stay) }
  }
  if (old && old.time !== row.time) {
    return { ...row, cls: 'is-changed', badge: { text: '시간 이동', cls: 'badge--danger' }, was: old.time, now: row.time, after: ' · ' + toDuration(row.stay) }
  }
  return { ...row, cls: '', badge: { text: '그대로', cls: 'badge--neutral' }, meta: toDuration(row.stay) }
}

const rows = computed(() => (replan.applied ? replan.applied.stops.map(toRow) : []))
// 적용하면서 뺀 곳 (원래 있었는데 결과에 없는 칸)
const gone = computed(() => {
  if (!replan.applied || !replan.before) return []
  return replan.before.filter((stop) => !replan.applied.stops.some((row) => row.id === stop.id))
})

const banner = computed(() => {
  const fixed = after.value && after.value.fixed
  if (!fixed) return { cls: 'banner--success', icon: 'i-success', title: replan.applied.message }
  const isLate = fixed.slackMin < 0
  return {
    cls: isLate ? 'banner--danger' : 'banner--success',
    icon: isLate ? 'i-danger' : 'i-success',
    title: `${fixed.name} ${fixed.eta} · ` + (isLate ? toDuration(-fixed.slackMin) + ' 늦어요' : '여유 ' + toDuration(fixed.slackMin)),
  }
})

// [카카오톡으로 공유]: 같이 간 사람에게 바뀐 일정을 보내요
//   여행 중 화면 [일정 공유]와 같은 ① 공유 링크를 써요
//   카톡 창에서 실제로 보냈는지는 앱이 알 수 없어서, 보낸 뒤에도 버튼 글자는 그대로 둬요
const shareText = computed(() => {
  const fixed = after.value && after.value.fixed
  const method = (replan.plan && replan.plan.title) || '' // 고른 대안 이름 (예: 지하철로 바로 가기)
  const arrive = fixed ? `${fixed.name} ${fixed.eta} 도착` : banner.value.title
  return arrive + (method ? ' · ' + method : '') + (fixed && fixed.slackMin >= 0 ? ' · 여유 ' + toDuration(fixed.slackMin) : '')
})
async function share() {
  try {
    const result = await shareTrip(replan.tripId, '일정이 바뀌었어요', shareText.value, '바뀐 일정 보기')
    if (result === 'copied') showToast('일정 링크를 복사했어요 · 메신저에 붙여 넣어 보내세요')
  } catch (e) {
    showToast('공유하지 못했어요 · ' + e.message)
  }
}

async function undo() {
  if (!replan.canUndo()) {
    canUndo.value = false
    return showToast('5분이 지나서 되돌릴 수 없어요')
  }
  isUndoing.value = true
  try {
    await replan.undo()
    router.replace('/live/delay')
    showToast('원래 일정으로 되돌렸어요')
  } catch (e) {
    showToast('되돌리는 중에 실패했어요 · ' + e.message)
  }
  isUndoing.value = false
}
</script>

<template>
  <div class="scr scr-Applied">
    <div class="app">
      <TopBar title="바뀐 일정" icon="close" to="/live">
        <button v-if="replan.applied && canUndo" type="button" class="btn btn--sm btn--secondary" style="margin-right: 8px" :disabled="isUndoing" @click="undo"><i class="ic ic--s i-undo" aria-hidden="true"></i>되돌리기</button>
      </TopBar>

      <div v-if="!replan.applied" class="scroll">
        <PageMessage title="방금 바꾼 일정이 없어요" text="여행 중 화면에서 대안을 고르면 여기에 보여요" to="/live" button="여행 중 화면으로" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 14px; bottom: 102px">
        <div class="banner" :class="banner.cls">
          <i class="ic ic--l" :class="banner.icon" aria-hidden="true"></i>
          <span class="banner__body">
            <span class="t-strong tn">{{ banner.title }}</span>
            <span v-if="banner.text" class="t-cap">{{ banner.text }}</span>
          </span>
        </div>

        <div class="legend" aria-label="색 안내">
          <span><i class="sw" style="background: var(--danger)"></i>바뀜</span>
          <span><i class="sw" style="background: var(--surface-raised); border: 1px solid var(--ink-subtle)"></i>그대로</span>
          <span><i class="sw" style="background: var(--surface-sunken); border: 1px dashed var(--line-strong)"></i>뺌</span>
          <span><i class="sw" style="background: var(--ink-subtle)"></i>다녀옴</span>
        </div>

        <ol class="tl" style="list-style: none; margin: 0; padding: 0">
          <template v-for="(row, i) in rows" :key="(row.id || '') + row.name">
            <li v-if="i > 0" class="leg"><span></span><span v-if="row.tag !== 'done' && legOf(row)"><i class="ic ic--s" :class="legOf(row).icon" aria-hidden="true"></i>이동 {{ toDuration(legOf(row).min) }}</span><span v-else></span></li>
            <li class="stop" :class="row.cls">
              <span class="stop__time">{{ row.time }}</span>
              <span class="stop__card">
                <span v-if="(row.tag !== 'done' || row.isCurrent) && isStation(row.category)" class="thumb thumb--station" aria-hidden="true"><i class="ic i-subway"></i></span>
                <img v-else-if="row.tag !== 'done' || row.isCurrent" class="thumb" :src="photoOf(row)" alt="" />
                <span class="stop__body">
                  <span class="stop__name">
                    <i v-if="row.tag === 'done' && !row.isCurrent" class="ic ic--s i-check c-success" aria-hidden="true"></i>
                    <i v-if="row.isFixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i>{{ row.name }}
                  </span>
                  <span v-if="row.was" class="stop__meta tn"><span class="was">{{ row.was }}</span><b class="c-danger">{{ row.now }}</b>{{ row.after }}</span>
                  <span v-else class="stop__meta tn">{{ row.meta }}</span>
                </span>
                <span v-if="row.isCurrent" class="me" aria-label="지금 위치" style="position: static; margin: 0 8px 0 0; flex: none"></span>
                <span v-if="row.badge" class="badge" :class="row.badge.cls">{{ row.badge.text }}</span>
              </span>
            </li>
          </template>
          <li v-for="stop in gone" :key="'gone' + stop.id" class="stop is-gone">
            <span class="stop__time"></span>
            <span class="stop__card">
              <img class="thumb" :src="photoOf(stop)" alt="" />
              <span class="stop__body"><span class="stop__name"><s>{{ stop.name }}</s></span><span class="stop__meta">일정에서 뺐어요</span></span>
              <span class="badge badge--neutral">뺌</span>
            </span>
          </li>
        </ol>

        <div v-if="replan.applied.nextAlert" class="banner banner--info">
          <i class="ic i-bell" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-cap">출발 10분 전에 알려 드려요</span></span>
        </div>
        
      </div>

      <div class="cta" style="display: flex; gap: 8px">
        <!-- [카카오톡으로 공유] 버튼: 카카오 디자인 가이드대로 노란 바탕(#FEE500), 검은 말풍선, 검은 글씨
             말풍선은 이미지 파일 없이 SVG로 그려요 -->
        <button type="button" class="btn btn--lg kakao-share" style="flex: 1" @click="share">
          <svg class="kakao-share__bubble" viewBox="0 0 24 22" aria-hidden="true"><path d="M12 1C5.9 1 1 4.9 1 9.7c0 3.1 2 5.8 5.1 7.3l-1.1 4c-.1.4.3.7.6.5l4.7-3.1c.6.1 1.1.1 1.7.1 6.1 0 11-3.9 11-8.7S18.1 1 12 1z" fill="#191919"/></svg>
          카카오톡으로 공유
        </button>
        <RouterLink to="/live" class="btn btn--primary btn--lg" style="flex: 1"><i class="ic i-trip" aria-hidden="true"></i>지도로 돌아가기</RouterLink>
      </div>
      </template>
    </div>
  </div>
</template>

<style>
.scr-Applied .legend{display:flex;gap:14px;align-items:center}
.scr-Applied .thumb--station{display:grid;place-items:center;background:var(--surface-sunken);color:var(--navy, var(--ink-muted))}
.scr-Applied .kakao-share{background:#FEE500;color:#191919;border:0;font-weight:700;gap:8px}
.scr-Applied .kakao-share__bubble{width:20px;height:18px;flex:none}
.scr-Applied .legend span{display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:18px;color:var(--ink-muted)}
.scr-Applied .legend i.sw{width:12px;height:12px;border-radius:4px;display:inline-block}
.scr-Applied .topbar .btn{flex:none}
.scr-Applied .stop.is-gone .stop__card{background:var(--surface-sunken);border:1px dashed var(--line-strong);opacity:.8}
.scr-Applied .was{font-size:13px;color:var(--ink-subtle);text-decoration:line-through;margin-right:4px}
</style>
