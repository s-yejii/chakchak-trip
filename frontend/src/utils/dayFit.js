// ③ 하루에 다 갈 수 있는지 어림 (11/20 피드백 "장소가 멀리 떨어져 있으면 며칠 필요한지 판단해 기간 늘려 주기")
// 담은 장소를 1.5km 안끼리 한 묶음으로 묶고(걸어서 도는 동네), 필요한 시간 = 머무는 시간 합 + 묶음 안 걷기 + 묶음 사이 이동
//   묶음 사이 이동은 길찾기를 부르지 않고 대중교통 어림(갈아타기·기다리기 15분 + 1km에 3분)으로 계산해요. 하루 활동 시간보다 길면 "2일로 나눌까요?"
//   (일정 만들기 전 안내용 어림이라 실제 일정은 서버가 길찾기로 다시 맞춰요)
import { reactive } from 'vue'

const CLUSTER_M = 1500     // 이 안끼리는 같은 동네 (걸어서 돌 수 있는 거리)
const WALK_M_PER_MIN = 67  // 걷는 속도 4km/h
const TRANSIT_BASE_MIN = 15 // 동네를 옮길 때 역까지 걷기·기다리기·갈아타기
const TRANSIT_MIN_PER_KM = 3
const STAY_MIN = { cafe: 50, food: 60, other: 60 } // 업종별 머무는 시간 기본값 (서버 칸 기본과 비슷하게)

// 담은 장소의 위치·종류 (WishLinks가 담을 때 넣고, 빼면 지움). 이름 → { lat, lng, category }
export const wishPlaces = reactive({})

function distanceM(a, b) {
  const dy = (b.lat - a.lat) * 111320
  const dx = (b.lng - a.lng) * 111320 * Math.cos((a.lat * Math.PI) / 180)
  return Math.hypot(dx, dy)
}

function stayOf(place) {
  const category = place.category || ''
  if (/카페|커피|디저트|베이커리|제과/.test(category)) return STAY_MIN.cafe
  if (/음식|식당|한식|일식|중식|양식|분식|고기|라멘|국수|요리|주점|술집|바/.test(category)) return STAY_MIN.food
  return STAY_MIN.other
}

// 1.5km 안으로 이어진 곳끼리 한 묶음 (한 곳이라도 가까우면 같은 묶음)
function makeClusters(places) {
  const clusters = []
  for (const place of places) {
    const near = clusters.filter((cluster) => cluster.some((other) => distanceM(place, other) <= CLUSTER_M))
    const merged = [place]
    for (const cluster of near) {
      merged.push(...cluster)
      clusters.splice(clusters.indexOf(cluster), 1)
    }
    clusters.push(merged)
  }
  return clusters
}

function centerOf(cluster) {
  let lat = 0
  let lng = 0
  for (const place of cluster) {
    lat += place.lat
    lng += place.lng
  }
  return { lat: lat / cluster.length, lng: lng / cluster.length }
}

// 묶음 안: 가까운 곳부터 차례로 걸을 때 걷는 분
function walkInside(cluster) {
  const left = [...cluster]
  let current = left.shift()
  let meters = 0
  while (left.length) {
    left.sort((a, b) => distanceM(current, a) - distanceM(current, b))
    meters += distanceM(current, left[0])
    current = left.shift()
  }
  return Math.round(meters / WALK_M_PER_MIN)
}

// 묶음 사이: 가까운 묶음부터 차례로 옮길 때 대중교통 어림 분
function moveBetween(clusters) {
  const left = clusters.map(centerOf)
  let current = left.shift()
  let minutes = 0
  while (left.length) {
    left.sort((a, b) => distanceM(current, a) - distanceM(current, b))
    minutes += TRANSIT_BASE_MIN + Math.round((distanceM(current, left[0]) / 1000) * TRANSIT_MIN_PER_KM)
    current = left.shift()
  }
  return minutes
}

/**
 * 담은 장소들을 하루에 다 갈 수 있는지. dayMinutes = 하루 활동 시간(분)
 * 돌려줌: { needMin, dayMin, groups, stayMin, walkMin, moveMin, isOver }
 */
export function checkDayFit(names, dayMinutes) {
  const places = []
  for (const name of names) {
    const place = wishPlaces[name]
    if (place && place.lat != null && place.lng != null) places.push(place)
  }
  const clusters = makeClusters(places)
  let stayMin = 0
  let walkMin = 0
  for (const place of places) stayMin += stayOf(place)
  for (const cluster of clusters) walkMin += walkInside(cluster)
  const moveMin = moveBetween(clusters)
  const needMin = stayMin + walkMin + moveMin
  return { needMin, dayMin: dayMinutes, groups: clusters.length, stayMin, walkMin, moveMin, isOver: places.length >= 2 && needMin > dayMinutes }
}
