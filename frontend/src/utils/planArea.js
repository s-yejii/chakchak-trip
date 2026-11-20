// 장소들의 주소에서 가장 많이 나온 구를 일정 만들기 지역으로 (③ 인스타로 담기·다시 짜기)
// places: [{ address, lat, lng }] → { name, fullName, type, lat, lng, isAuto } 좌표는 그 구 장소들의 가운데. 못 찾으면 null
export function areaFromPlaces(places) {
  const groups = {}
  for (const place of places) {
    const match = /^(\S+)\s+(\S+(?:구|군|시))(?:\s|$)/.exec(place.address || '')
    if (match && place.lat != null) {
      const key = match[1] + ' ' + match[2]
      if (!groups[key]) groups[key] = { name: match[2], fullName: key, list: [] }
      groups[key].list.push(place)
    }
  }
  let best = null
  for (const key in groups) {
    if (!best || groups[key].list.length > best.list.length) best = groups[key]
  }
  if (!best) return null
  const count = best.list.length
  const lat = best.list.reduce((sum, place) => sum + place.lat, 0) / count
  const lng = best.list.reduce((sum, place) => sum + place.lng, 0) / count
  return { name: best.name, fullName: best.fullName, type: 'gu', lat, lng, isAuto: true }
}

// 담은 장소들이 있는 구 전부 [{ name, lat, lng, dongs: [] }] (11/20: 인스타로 담은 곳이 여러 구면 지도에 다 골라 두려고, GuMapPicker 모양)
export function areasFromPlaces(places) {
  const groups = {}
  for (const place of places) {
    const match = /^(\S+)\s+(\S+(?:구|군|시))(?:\s|$)/.exec(place.address || '')
    if (!match || place.lat == null) continue
    if (!groups[match[2]]) groups[match[2]] = []
    groups[match[2]].push(place)
  }
  const areas = []
  for (const name in groups) {
    const list = groups[name]
    const lat = list.reduce((sum, place) => sum + place.lat, 0) / list.length
    const lng = list.reduce((sum, place) => sum + place.lng, 0) / list.length
    areas.push({ name, lat, lng, dongs: [] })
  }
  return areas
}

// 지역을 아직 안 골랐으면 예약한 곳(장소 검색·캡처)의 구로 자동 (11/20: 꼭 갈 곳 칸에서 예약만 넣어도 "어디로"를 건너뛰게)
export function setAutoAreaFrom(store, place) {
  if (store.planArea || !place || place.lat == null) return
  const area = areaFromPlaces([place])
  if (!area) return
  store.planArea = area
  store.planAreas = areasFromPlaces([place])
}
