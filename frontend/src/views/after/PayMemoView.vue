<script setup>
// S13-f 결제 메모 (⑤ 화면 · 11/16 ③ 주형이 연결, 석진 OK): 여행 중에 쓴 돈을 영수증 사진이나 직접 입력으로 적어 둬요
// 예전엔 가짜 값(쭈꾸미골목 32,000원 · 나·지은·엄마·민지·준호)만 보였어요. 이제 진짜로:
//   영수증 사진 → 서버가 읽기(POST /api/plans/capture/receipt, ③) → 가게·금액·시각 확인(고칠 수 있게) → [저장]
//   → 이 여행 결제 기록(POST /api/trips/{id}/payments, ⑤)에 저장 → 여행 뒤 석진 회고 화면(RetroView)에 그대로 보여요
// 앱을 처음 쓰는 사람 기준(예지): 맨 위에 할 일 한 줄, 결제가 0개면 빈 화면 안내, 사진 없이 직접 적기도 가능
// 사진은 서버에 저장하지 않아요 (읽기만). 저장 방식은 회고 화면의 savePayForm·readReceipt와 같아요 (새 저장 방식 안 만듦)
import { computed, onMounted, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { get, post, upload } from '@/api/http'
import { useTripStore } from '@/stores/trip'
import { toWon } from '@/utils/time'
import { showToast } from '@/utils/toast'

const MAX_AMOUNT = 10000000 // 1천만 원 (서버 영수증 읽기와 같은 상한)

const store = useTripStore()
const tripId = ref(null)
const payments = ref([])   // 이 여행 결제 기록 (결제 순)
const isLoading = ref(true)
const isReading = ref(false) // 영수증 읽는 중
const isSaving = ref(false)
const form = ref(null)       // 적는 칸 { storeName, amount, paidAt, source, note }. null이면 닫힘
const receiptInput = ref(null)

const total = computed(() => payments.value.reduce((sum, payment) => sum + payment.amount, 0))
const canSave = computed(() => {
  if (!form.value) return false
  const amount = Number(form.value.amount)
  return form.value.storeName.trim() !== '' && amount >= 1 && amount <= MAX_AMOUNT && form.value.paidAt !== ''
})

onMounted(async () => {
  tripId.value = await store.findOngoingTripId() // 진행 중(없으면 오늘 날짜) 여행
  await loadPayments()
})

async function loadPayments() {
  if (!tripId.value) {
    isLoading.value = false
    return
  }
  try {
    payments.value = await get('/trips/' + tripId.value + '/payments')
  } catch (e) {
    showToast(e.message)
  }
  isLoading.value = false
}

// 지금 시각 "YYYY-MM-DDTHH:mm" (직접 적기 기본값)
function nowText() {
  const now = new Date()
  const pad = (number) => String(number).padStart(2, '0')
  return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + 'T' + pad(now.getHours()) + ':' + pad(now.getMinutes())
}

// [직접 적기]: 빈 칸으로 열기
function openManual() {
  form.value = { storeName: '', amount: '', paidAt: nowText(), source: 'manual', note: '' }
}

// [영수증 올리기]: 숨겨 둔 사진 고르기 칸을 대신 눌러 줌
function openReceipt() {
  receiptInput.value.click()
}

// 사진을 고르면: 서버가 가게·금액·시각을 읽어 적는 칸에 채움 (저장은 사용자가 확인하고 [저장])
async function readReceipt(event) {
  const file = event.target.files[0]
  event.target.value = '' // 같은 사진을 다시 골라도 되게
  if (!file) return
  isReading.value = true
  try {
    const formData = new FormData()
    formData.append('image', file)
    const result = await upload('/plans/capture/receipt', formData)
    form.value = {
      storeName: result.storeName || '',
      amount: result.totalAmount || '',
      paidAt: result.paidAt ? result.paidAt.replace(' ', 'T') : nowText(),
      source: 'capture',
      note: result.checkNeeded ? result.note : '',
    }
  } catch (e) {
    showToast(e.message + ' · 직접 적을 수도 있어요')
  }
  isReading.value = false
}

// [저장]: 이 여행 결제 기록에 저장하고 목록 다시 읽기
async function save() {
  if (!canSave.value || isSaving.value) return
  isSaving.value = true
  try {
    await post('/trips/' + tripId.value + '/payments', {
      paidAt: form.value.paidAt,
      storeName: form.value.storeName.trim(),
      amount: Number(form.value.amount),
      source: form.value.source,
    })
    form.value = null
    showToast('저장했어요 · 여행이 끝나면 회고에서도 볼 수 있어요')
    await loadPayments()
  } catch (e) {
    showToast(e.message)
  }
  isSaving.value = false
}

// 금액 칸: 숫자만
function setAmount(text) {
  const digits = text.replace(/\D/g, '').slice(0, 8)
  form.value.amount = digits === '' ? '' : Number(digits)
}
</script>

<template>
  <div class="scr scr-PayMemo">
    <div class="app">
      <TopBar title="결제 메모" back="/live" />
      <div class="scroll" style="padding-top: 8px; gap: 16px; bottom: 24px">
        <PageMessage v-if="isLoading" is-loading title="결제 기록을 불러오는 중이에요" />
        <PageMessage v-else-if="!tripId" icon="i-info" title="진행 중인 여행이 없어요" text="여행을 시작하면 쓴 돈을 여기에 적어 둘 수 있어요" to="/trips" button="내 여행 보기" />

        <template v-else>
          <!-- 처음 쓰는 사람용: 할 일 한 줄 + 사진 안내 -->
          <section class="pm-guide" aria-label="결제 메모 안내">
            <p class="t-strong" style="margin: 0">영수증 사진을 올리면 가게·금액·시간을 채워 줘요</p>
            <p class="t-cap" style="margin: 0"><i class="ic ic--s i-info" aria-hidden="true"></i> 사진은 글자만 읽고 저장하지 않아요 · 카드번호는 가려서 올려 주세요</p>
            <div class="cta__row">
              <button type="button" class="btn btn--primary btn--md" :disabled="isReading" @click="openReceipt"><i class="ic i-camera" aria-hidden="true"></i>{{ isReading ? '읽는 중…' : '영수증 올리기' }}</button>
              <button type="button" class="btn btn--secondary btn--md" :disabled="isReading" @click="openManual">직접 적기</button>
            </div>
            <input ref="receiptInput" type="file" accept="image/*" hidden @change="readReceipt" />
          </section>

          <!-- 적는 칸: 읽은 값 확인·고치기 -->
          <section v-if="form" class="card card--pad pm-form" aria-label="결제 적기">
            <p v-if="form.source === 'capture'" class="t-cap" style="margin: 0">영수증에서 읽었어요 · 틀리면 고쳐 주세요</p>
            <p v-if="form.note" class="field__help c-warning" style="margin: 0"><i class="ic ic--s i-warning" aria-hidden="true"></i>{{ form.note }}</p>
            <label class="field">
              <span class="field__label">가게 이름</span>
              <span class="field__box"><input v-model="form.storeName" type="text" maxlength="60" placeholder="예) 남도분식 익선점" autocomplete="off" /></span>
            </label>
            <label class="field">
              <span class="field__label">금액(원)</span>
              <span class="field__box"><input :value="form.amount === '' ? '' : Number(form.amount).toLocaleString('ko-KR')" type="text" inputmode="numeric" placeholder="0" autocomplete="off" @input="setAmount($event.target.value)" /></span>
            </label>
            <label class="field">
              <span class="field__label">결제 시각</span>
              <span class="field__box"><input v-model="form.paidAt" type="datetime-local" /></span>
            </label>
            <div class="cta__row">
              <button type="button" class="btn btn--secondary btn--md" @click="form = null">취소</button>
              <button type="button" class="btn btn--primary btn--md" :disabled="!canSave || isSaving" @click="save">저장</button>
            </div>
          </section>

          <!-- 이 여행 결제 기록 -->
          <section class="pm-list-sec" aria-label="이 여행 결제 기록">
            <div class="pm-head">
              <h2 class="h-title" style="margin: 0">이 여행에서 쓴 돈</h2>
              <span v-if="payments.length" class="t-strong tn">{{ toWon(total) }}</span>
            </div>
            <p v-if="!payments.length" class="pm-empty t-cap">아직 적은 결제가 없어요 · 영수증을 올리거나 직접 적어 보세요</p>
            <ul v-else class="pm-list">
              <li v-for="payment in payments" :key="payment.id" class="pm-row">
                <span class="pm-row__body">
                  <b class="t-strong">{{ payment.storeName }}</b>
                  <span class="t-cap tn">{{ payment.day }}일차 {{ payment.paidAt }} · {{ payment.source === 'capture' ? '영수증' : payment.source === 'card' ? '카드 내역' : '직접 적음' }}</span>
                </span>
                <b class="tn">{{ toWon(payment.amount) }}</b>
              </li>
            </ul>
            <p class="t-cap" style="margin: 0">여행이 끝나면 회고 화면에서 누가 얼마 냈는지 나눌 수 있어요</p>
          </section>
        </template>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PayMemo .pm-guide{display:flex;flex-direction:column;gap:10px}
.scr-PayMemo .pm-guide .cta__row .btn{flex:1}
.scr-PayMemo .pm-form{display:flex;flex-direction:column;gap:12px}
.scr-PayMemo .pm-list-sec{display:flex;flex-direction:column;gap:10px}
.scr-PayMemo .pm-head{display:flex;align-items:center;justify-content:space-between;gap:8px}
.scr-PayMemo .pm-empty{margin:0;padding:20px 12px;text-align:center;border:1px dashed var(--line-strong);border-radius:var(--radius-md)}
.scr-PayMemo .pm-list{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised);overflow:hidden}
.scr-PayMemo .pm-row{display:flex;align-items:center;gap:12px;min-height:56px;padding:8px 14px;border-top:1px solid var(--line)}
.scr-PayMemo .pm-row:first-child{border-top:0}
.scr-PayMemo .pm-row__body{display:flex;flex-direction:column;gap:2px;flex:1;min-width:0}
</style>
