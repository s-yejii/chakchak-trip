// 여행 시간 추천 (③, 11/06): "시간 추천" 버튼이 쓰는 시작·끝 시각
// 기본은 점심부터 저녁까지 11:00~20:00 (통계가 아니라 정한 기본값이라, 화면에도 "통계"라고 말하지 않아요)
// 11/14: 늘 11:00~20:00이던 것 → 이미 넣은 예약 시각에 맞춰 시작을 당기거나 끝을 늘려요 (fixTimes)
const RECOMMENDED = { start: '11:00', end: '20:00' } // 점심부터 저녁까지 (기본값)
const BEFORE_FIXED_MIN = 60 // 이른 예약이면 그보다 1시간 앞에서 시작
const AFTER_FIXED_MIN = 120 // 늦은 예약이면 2시간 머물 수 있게 끝을 늘림 (식사 예약이 대부분)
const LATEST_END_MIN = 23 * 60 + 30

// 추천 시작·끝 시각 { start, end }
export function recommendTime() {
  return { ...RECOMMENDED }
}

// ---- 11/09: 오늘 여행이면 추천 시작을 지금 이후로 (추천 11:00인데 지금 11:43이면 내일로 넘기던 문제) ----
export const PREP_MIN = 15 // 나갈 준비 시간: 지금부터 최소 이만큼 뒤에 시작
export const STEP_MIN = 30 // 시각은 30분 단위
export const MIN_SPAN_MIN = 120 // 최소 2시간은 놀 수 있게
export const LAST_START_MIN = 21 * 60 // 이보다 늦게 시작해야 하면 오늘은 못 놀아서 내일로
const DAY_END_MIN = 24 * 60

const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5))
const toTime = (minutes) => String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')

// 이미 넣은 예약 시각(["09:30", "19:00"])에 맞춰 기본 시간을 넓힘. 바뀐 이유도 같이 { start, end, why }
export function fitToFixed(base, fixedTimes) {
  let start = toMinutes(base.start)
  let end = toMinutes(base.end)
  let why = ''
  for (const time of fixedTimes || []) {
    if (!/^\d{2}:\d{2}$/.test(time)) continue
    const minutes = toMinutes(time)
    if (minutes - BEFORE_FIXED_MIN < start) {
      start = Math.max(0, Math.floor((minutes - BEFORE_FIXED_MIN) / STEP_MIN) * STEP_MIN)
      why += '예약 ' + time + '에 맞춰'
    }
    if (minutes + AFTER_FIXED_MIN > end) {
      end = Math.min(LATEST_END_MIN, Math.ceil((minutes + AFTER_FIXED_MIN) / STEP_MIN) * STEP_MIN)
      why += '예약 ' + time + '에 맞춰'
    }
  }
  const changedCount = (why.match(/예약/g) || []).length
  return { start: toTime(start), end: toTime(end), why: changedCount > 1 ? '예약 시각에 맞춰' : why }
}

// 오늘인지·지금(분)·넣은 예약 시각에 맞춘 추천 { start, end, isTomorrow, why }. isTomorrow면 날짜를 내일로 (그때만 "늦어서 내일로")
export function recommendTimeFor(isToday, nowMin, fixedTimes = []) {
  const base = fitToFixed(recommendTime(), fixedTimes)
  const baseStart = toMinutes(base.start)
  if (!isToday || baseStart >= nowMin + PREP_MIN) return { ...base, isTomorrow: false }
  const start = Math.ceil((nowMin + PREP_MIN) / STEP_MIN) * STEP_MIN
  if (start > LAST_START_MIN) return { ...base, isTomorrow: true }
  const end = Math.min(DAY_END_MIN, Math.max(toMinutes(base.end), start + MIN_SPAN_MIN))
  return { start: toTime(start), end: toTime(end), isTomorrow: false, why: base.why }
}
