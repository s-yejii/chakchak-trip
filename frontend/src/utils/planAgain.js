// ③ 지난 여행 코스로 다시 일정 만들기 (11/20 피드백 PPT 17장: 회고의 [이 코스로 다시]가 늘 골목 테마로 가던 문제, ⑤ 석진 OK)
// 홈 "다시 짜기"(MainView replan)와 같은 방식: 지역·인원·이동 수단·다녀온 가게(꼭 갈 곳)만 가져오고, 날짜·예약은 새로 골라요
import { getTrip } from '@/api/trips'
import { areaFromPlaces } from '@/utils/planArea'
import { isStayStop } from '@/utils/mapView'
import { MOVE_CODES } from '@/utils/styleCodes'
import { planStepIndex } from '@/utils/planSteps'

/** 여행 id로 일정 만들기 조건을 채워요. 못 읽으면 false (그냥 처음부터) */
export async function fillPlanFromTrip(store, tripId) {
  let trip
  try {
    trip = await getTrip(tripId)
  } catch {
    return false
  }
  store.resetPlan()
  const stops = trip.stops || []
  store.planPeople = trip.people || store.planPeople
  store.planMoves = Object.keys(MOVE_CODES).filter((label) => (trip.transports || []).includes(MOVE_CODES[label]))
  store.planArea = areaFromPlaces(stops)
  // 다녀온 가게만 (빠진 칸·숙소·예약 칸은 빼요. 예약은 날짜가 달라서 새로 넣어요)
  const visited = stops.filter((stop) => stop.state !== 'skipped' && !stop.fixed && !isStayStop(stop))
  const names = []
  for (const stop of visited) {
    if (!names.includes(stop.name)) names.push(stop.name)
  }
  store.planConditions = { mustVisit: names }
  store.planStep = planStepIndex(store.planArea ? 'when' : 'where')
  return true
}
