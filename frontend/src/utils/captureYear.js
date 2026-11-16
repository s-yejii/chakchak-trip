// ③ 캡처에서 읽은 날짜를 여행 날짜에 맞추기 (11/16)
// 서버는 연도가 없는 날짜를 "오늘 이후 가장 가까운 날"로 정해요(CaptureDates). 그런데 여행 날짜가 이미 정해져 있으면
// 그 여행 안의 같은 월·일이 맞아요. 예) 오늘 11/16, 여행 2026-11-14~15, 캡처 "11/14" → 서버 2027-11-14 → 여기서 2026-11-14
// 여행 기간 안에 같은 월·일이 없으면 읽은 그대로 둬요 (여행 날짜 밖이면 화면이 따로 알려 줘요)

const YEAR_RANGE = [-1, 0, 1] // 읽은 연도 앞뒤 한 해까지만 봄

// readDate: 'YYYY-MM-DD' · start, end: 여행 시작·끝 'YYYY-MM-DD' (끝이 없으면 시작과 같게)
export function fitYearToTrip(readDate, start, end) {
  if (!readDate || !start || !/^\d{4}-\d{2}-\d{2}$/.test(readDate)) return readDate
  const last = end || start
  if (readDate >= start && readDate <= last) return readDate
  const year = Number(readDate.slice(0, 4))
  for (const diff of YEAR_RANGE) {
    const candidate = String(year + diff) + readDate.slice(4)
    if (candidate >= start && candidate <= last) return candidate
  }
  return readDate
}
