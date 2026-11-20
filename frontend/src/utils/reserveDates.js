// ③ 날짜를 고르기 전(1/6 "꼭 갈 곳")에 넣는 예약으로 여행 기간 맞추기 (11/20 검증)
// 1/6에서는 날짜가 아직 기본값(오늘, 당일치기)이라 2일차 예약이 "여행 날짜 밖"으로 막혔어요.
// 그래서 예약 날짜가 들어가게 기간을 늘려요: 가장 이른 예약 = 첫날, 가장 늦은 예약 = 마지막 날.
// 3/6 "언제 가요?"에서는 이 기간이 미리 잡혀 있고 바꿀 수 있어요 (바꾼 기간 밖 예약은 그 화면이 알려 줘요)
import { addDays, daysBetween } from '@/utils/dates'
import { planStepIndex } from '@/utils/planSteps'

const MAX_TRIP_DAYS = 30 // 여행은 30일까지 (서버 TripServiceImpl.MAX_TRIP_DAYS와 같게)

/** 아직 "언제 가요?" 단계 전인지 (1/6·2/6에서 연 예약 화면) */
export function isBeforeWhenStep(store) {
  return store.planStep < planStepIndex('when')
}

/** 예약 날짜(yyyy-MM-dd)가 들어가게 여행 기간을 늘려요. 30일을 넘으면 그대로 두고 false */
export function stretchTripTo(store, date) {
  const oldStart = store.planStartDate
  const oldEnd = store.planEndDate || oldStart
  const hasFixed = ((store.planConditions || {}).fixed || []).length > 0
  let start = oldStart
  let end = oldEnd
  if (!hasFixed && (date < oldStart || date > oldEnd)) {
    // 첫 예약이 기간 밖이면 그날을 첫날로 (박 수는 그대로, 예전 moveTripStart와 같음)
    start = date
    end = addDays(date, daysBetween(oldStart, oldEnd))
  } else {
    if (date < start) start = date
    if (date > end) end = date
  }
  if (daysBetween(start, end) >= MAX_TRIP_DAYS) return false
  // 첫날이 앞당겨지면 이미 넣은 예약의 "며칠째"를 그만큼 뒤로 (날짜는 그대로)
  if (hasFixed && start < oldStart) shiftFixedDays(store, daysBetween(start, oldStart))
  if (start !== oldStart) store.planFromTime = '' // 첫날이 바뀌면 여행 정보로 돌아갈 때 그날 기준 시간으로 다시 채워요
  store.planStartDate = start
  store.planEndDate = start === end ? null : end
  return true
}

// 예약 칸(조건 fixed + 초안의 고정 칸)의 일차를 days만큼 뒤로
function shiftFixedDays(store, days) {
  const fixed = []
  for (const item of store.planConditions.fixed) {
    fixed.push({ ...item, day: (item.day || 1) + days })
  }
  store.planConditions = { ...store.planConditions, fixed }
  for (const stop of store.draft.stops) {
    if (stop.fixed) stop.day = (stop.day || 1) + days
  }
}
