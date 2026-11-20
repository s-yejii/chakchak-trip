// 지도 그림 위 위치 계산: 화면의 지도는 그림(mapB.svg)이라, 진짜 좌표(위도·경도)를 그림 안 x, y(px)로 바꿔요.
// 장소들이 지도 칸(box) 안에 고르게 들어오게 늘이고 줄여요. 위도는 위로 갈수록 커서 y는 거꾸로예요.

const PAD = 0.15 // 가장자리 여백 비율

// points: [{ lat, lng }] → [{ x, y }] (같은 순서). box: { left, top, width, height }
export function toMapPositions(points, box) {
  const valid = points.filter((p) => p && p.lat != null && p.lng != null)
  if (valid.length === 0) return points.map(() => ({ x: box.left + box.width / 2, y: box.top + box.height / 2 }))
  let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity
  for (const p of valid) {
    minLat = Math.min(minLat, p.lat)
    maxLat = Math.max(maxLat, p.lat)
    minLng = Math.min(minLng, p.lng)
    maxLng = Math.max(maxLng, p.lng)
  }
  const latSpan = Math.max(maxLat - minLat, 0.002) // 한 곳뿐이어도 0으로 나누지 않게
  const lngSpan = Math.max(maxLng - minLng, 0.002)
  return points.map((p) => {
    if (!p || p.lat == null) return { x: box.left + box.width / 2, y: box.top + box.height / 2 }
    const rx = (p.lng - minLng) / lngSpan
    const ry = (maxLat - p.lat) / latSpan
    return {
      x: Math.round(box.left + box.width * (PAD + rx * (1 - PAD * 2))),
      y: Math.round(box.top + box.height * (PAD + ry * (1 - PAD * 2))),
    }
  })
}

// 같은 숙소 핀인지 (같은 장소 번호이거나 같은 좌표): 1일차 체크인·밤 입실처럼 숙소 칸이 여러 개여도 핀은 하나만 (11/09 ② 찬기 OK)
export function isSameStay(a, b) {
  if (a.placeId && b.placeId) return a.placeId === b.placeId
  return a.lat === b.lat && a.lng === b.lng
}

// 숙소 칸인지 (일정 만들기가 넣는 숙소 칸은 category가 '숙소', 11/04)
export function isStayStop(stop) {
  return stop.category === '숙소'
}

// 분류로 고르는 기본 사진 (DB 장소 사진이 없을 때)
export function imageFor(category) {
  const c = category || ''
  if (/숙소|호텔|숙박|모텔|게스트하우스/.test(c)) return '/img/stay.svg' // 11/17: 사진 없는 숙소·터미널에 감자탕 사진이 나오던 문제
  if (/터미널|정차역|기차역|지하철역|고속버스|KTX|공항/.test(c)) return '/img/terminal.svg' // 버스터미널 등 교통 종류 (11/19 ①)
  // 11/19 ①: 사진 없는 칸에 국밥·골목 같은 엉뚱한 진짜 사진 대신 종류별 아이콘 (팀 규칙: 사진 없으면 아이콘)
  if (!c) return '/img/place.svg'
  if (/카페|커피|디저트|베이커리|케이크|도넛|빵/.test(c)) return '/img/cafe.svg'
  if (/이자카야|주점|술집|맥주|호프|포장마차|와인|바\(BAR\)/.test(c)) return '/img/bar.svg'
  if (/공연|문화|콘서트|극장|전시|미술관|박물관/.test(c)) return '/img/culture.svg'
  if (/쇼핑|소품|편집|백화점|시장/.test(c)) return '/img/shop.svg'
  if (/관광|거리|공원|산책|명소|축제|레포츠/.test(c)) return '/img/tour.svg'
  return '/img/food.svg'
}
