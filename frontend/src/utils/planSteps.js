// ③ 여행 정보(일정 만들기) 단계 순서 (11/20 피드백: 11단계 → 7단계)
// 다른 화면(홈 다시 짜기·테마·줄이기)이 "몇 번째 단계로 보내기"를 숫자로 적어 두면 단계가 바뀔 때마다 틀려서, 이름으로 찾게 한 곳에 모았어요
//   link 링크 · where 어디로 · when 언제(+숙소) · from 출발지 · who 누구랑 · kind 어디 위주+이런 곳 · fixed 예약한 곳(+시간·이동 수단 확인)
export const PLAN_STEP_IDS = ['link', 'where', 'when', 'from', 'who', 'kind', 'fixed']

/** 단계 이름 → 번호 (없으면 0) */
export function planStepIndex(id) {
  const index = PLAN_STEP_IDS.indexOf(id)
  return index < 0 ? 0 : index
}
