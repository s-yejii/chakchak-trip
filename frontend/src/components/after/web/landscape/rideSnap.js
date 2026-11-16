// ⑤ 버스·택시로 간 구간을 찻길에 맞추기 (맵 매칭, 11/16)
// 왜: 앱은 GPS를 20초마다 저장 → 버스(시속 30km)는 점 사이가 약 170m. 점을 직선으로 이으면 모퉁이에서 건물·숲을 뚫고 지나감
// 어떻게: 찻길 선들을 "점과 이음"(그래프)으로 바꾸고, GPS 점마다 가까운 찻길 점 몇 개를 후보로 둠
//   → 후보를 하나씩 골라 이었을 때 "길 따라 간 거리 + GPS와 떨어진 거리"의 합이 가장 작은 조합을 찾음 (맵 매칭의 표준 방법)
//   → 고른 후보 사이를 찻길을 따라 가장 짧은 길로 이음
//   가장 짧은 길 찾기 = 다익스트라: 출발점에서 가까운 점부터 차례로 "여기까지 온 거리"를 확정해 나가는 방법
// 걷기·지하철·배 구간은 그대로 둠 (지하철은 땅속, 걷기는 20초에 25m쯤이라 직선으로 이어도 티가 안 남)

const NODE_STEP_M = 15       // 찻길 선을 이 간격으로 잘게 나눠 점을 놓음 (GPS 점을 가까운 찻길 점에 붙이려고)
const JOIN_M = 3             // 길의 원래 꼭짓점끼리 이 안에 있으면 같은 점으로 봄 (교차로 · 지도 조각 경계에서 나뉜 길)
const SNAP_M = 45            // GPS 점에서 이 안에 있는 찻길 점만 후보 (도심 GPS 오차 10~30m보다 조금 크게)
const GPS_WEIGHT = 2         // 후보 고르기: 길 따라 간 거리 + (GPS 점과 떨어진 거리 × 이 값)
const MAX_CANDIDATES = 6     // GPS 점 하나의 후보 수
const CANDIDATE_GAP_M = 20   // 후보끼리는 이만큼 떨어지게 (같은 길의 바로 옆 점만 여러 개 고르지 않게 → 옆 차로·옆 길도 후보에 듦)
const JUMP_COST = 1000       // 찻길로 이어지지 않는 두 후보 사이(직선으로 건너뜀)에 더하는 값. 커서 웬만하면 안 고름
const NO_ROAD = -1           // 가까운 찻길이 없는 GPS 점의 후보 (GPS 자리 그대로 씀)
const DETOUR_TIMES = 3       // 길 따라 간 거리가 "직선 거리 × 이 값 + DETOUR_EXTRA_M"를 넘으면 이어지지 않는 것으로 봄
const DETOUR_EXTRA_M = 150
const GRAPH_MARGIN_M = 300   // 탈것 구간을 감싸는 네모에서 이만큼 더 넓게 찻길을 모음
const MAIN_ROAD_M = 5        // 폭이 이보다 좁은 길(골목·주차장 길)은
const SMALL_ROAD_TIMES = 2.5 //   거리를 이 배수로 쳐서 덜 고르게 (버스가 골목으로 질러가지 않게)
const LOOP_MAX_M = 400       // 같은 찻길 점으로 되돌아온 길이 이보다 짧으면 잘못 들어갔다 나온 것으로 보고 지움
const SPIKE_COS = -0.7       // 꺾인 각의 코사인이 이보다 작으면(약 135도 넘게 뒤로 꺾임) 그 점을 지움
const MODE_TAXI = 1
const MODE_BUS = 4

const isRoadRide = (mode) => mode === MODE_TAXI || mode === MODE_BUS

/**
 * route의 버스·택시 구간을 찻길을 따라가는 점들로 바꿈 (route를 직접 고침)
 * route: { points: [x, z, …], minutes: [], modes: [점 → 다음 점 이동 방법], hints: [] }, roads: [{ points, width, kind }] (미터 좌표, 자르기 전 전체)
 */
export function snapRidesToRoads(route, roads) {
  const count = route.minutes.length
  const result = { points: [], minutes: [], modes: [], hints: [] }
  let i = 0
  while (i < count) {
    if (i === count - 1 || !isRoadRide(route.modes[i])) {
      addPoint(result, route.points[i * 2], route.points[i * 2 + 1], route.minutes[i], route.modes[i], route.hints[i])
      i++
      continue
    }
    let end = i // 탈것에서 내린 점 (i ~ end가 한 번 탄 구간)
    while (end < count - 1 && isRoadRide(route.modes[end])) end++
    addSnappedRide(route, i, end, roads, result)
    i = end // 내린 점은 다음 바퀴에서 그대로 넣음
  }
  route.points = result.points
  route.minutes = result.minutes
  route.modes = result.modes
  route.hints = result.hints
}

function addPoint(result, x, z, minute, mode, hint) {
  result.points.push(Math.round(x * 100) / 100, Math.round(z * 100) / 100)
  result.minutes.push(Math.round(minute * 1000) / 1000)
  result.modes.push(mode)
  result.hints.push(hint || null)
}

// 한 번 탄 구간(first ~ last번 점)을 찻길에 맞춰 result에 넣음. 탄 점(first)은 그대로, 내린 점(last)은 넣지 않음
//   1) 고르기: GPS 점마다 찻길 점 하나씩 (chooseNodes)
//   2) 잇기: 고른 점 사이를 찻길 따라 이어 items(지나가는 자리 목록)를 만듦
//      anchors = "GPS 점 k는 items의 몇 번째 자리에, 몇 분에 있었다" (나중에 시각을 나눌 때 씀)
//   3) 다듬기: 옆 골목으로 들어갔다 되돌아 나온 곳·뒤로 꺾인 점을 지움
//   4) 넣기: anchors 사이의 자리들에 시각을 간 거리에 비례해 나눠 줌
function addSnappedRide(route, first, last, roads, result) {
  const gps = [] // 이 구간의 GPS 점들 [{ x, z, minute }]
  for (let k = first; k <= last; k++) gps.push({ x: route.points[k * 2], z: route.points[k * 2 + 1], minute: route.minutes[k] })
  const graph = makeGraph(roads, boxOfPoints(gps))
  const chosen = chooseNodes(graph, gps)
  const spotOf = (k) => (chosen[k] === NO_ROAD ? { x: gps[k].x, z: gps[k].z, node: NO_ROAD } : { x: graph.xs[chosen[k]], z: graph.zs[chosen[k]], node: chosen[k] })

  const items = [{ x: gps[0].x, z: gps[0].z, node: NO_ROAD }] // 탄 점 (GPS 그대로)
  const anchors = [{ at: 0, minute: gps[0].minute }]
  if (chosen[0] !== NO_ROAD) items.push(spotOf(0))
  for (let k = 1; k < gps.length; k++) {
    const path = chosen[k - 1] === NO_ROAD || chosen[k] === NO_ROAD ? null : pathBetween(graph, chosen[k - 1], chosen[k])
    if (path) {
      for (let n = 1; n < path.length; n++) items.push({ x: graph.xs[path[n]], z: graph.zs[path[n]], node: path[n] })
    } else {
      items.push(spotOf(k)) // 찻길로 못 이음 → 직선으로 건너뜀
    }
    if (k === gps.length - 1) items.push({ x: gps[k].x, z: gps[k].z, node: NO_ROAD }) // 내린 점 (시각을 나누는 데만 쓰고 넣지는 않음)
    anchors.push({ at: items.length - 1, minute: gps[k].minute })
  }
  removeLoops(items, anchors)
  removeSpikes(items, anchors)
  addTimedItems(result, items, anchors, route.modes[first])
}

// GPS 점마다 찻길 점을 하나씩 고름. 돌려줌: 점 번호 목록 (가까운 찻길이 없는 점은 NO_ROAD)
//   앞에서부터 하나씩 "그때 가장 좋은 점"을 고르면, 잘못 든 옆길에서 빠져나오느라 멀리 돌게 됨
//   → 후보마다 "여기까지 오는 가장 싼 값"과 "바로 앞에서 어느 후보였는지"를 적어 가고, 끝에서 거꾸로 되짚음
function chooseNodes(graph, gps) {
  const candidates = [] // GPS 점마다 후보 점 번호 목록
  const scores = []     // 후보마다 여기까지 오는 가장 싼 값
  const backs = []      // 후보마다 바로 앞 GPS 점에서 고른 후보의 순서 번호
  for (let k = 0; k < gps.length; k++) {
    candidates.push(candidatesNear(graph, gps[k].x, gps[k].z))
    scores.push([])
    backs.push([])
    for (const node of candidates[k]) {
      scores[k].push(k === 0 ? offRoadCost(graph, node, gps[k]) : Infinity)
      backs[k].push(0)
    }
    if (k > 0) scoreStep(graph, gps, k, candidates, scores, backs)
  }
  const chosen = new Array(gps.length)
  const lastScores = scores[gps.length - 1]
  let pick = 0 // 마지막 GPS 점에서 값이 가장 싼 후보
  for (let c = 1; c < lastScores.length; c++) {
    if (lastScores[c] < lastScores[pick]) pick = c
  }
  for (let k = gps.length - 1; k >= 0; k--) {
    chosen[k] = candidates[k][pick]
    pick = backs[k][pick]
  }
  return chosen
}

// k번 GPS 점의 후보마다: 앞 점의 어느 후보에서 오는 것이 가장 싼지 찾아 scores·backs에 적음
function scoreStep(graph, gps, k, candidates, scores, backs) {
  const straight = Math.hypot(gps[k].x - gps[k - 1].x, gps[k].z - gps[k - 1].z)
  for (let b = 0; b < candidates[k - 1].length; b++) {
    const from = candidates[k - 1][b]
    const reach = from === NO_ROAD ? null : spread(graph, from, straight * DETOUR_TIMES + DETOUR_EXTRA_M)
    for (let c = 0; c < candidates[k].length; c++) {
      const to = candidates[k][c]
      const isJoined = reach !== null && to !== NO_ROAD && reach.costs.has(to)
      const move = isJoined ? reach.costs.get(to) : JUMP_COST + straight
      const score = scores[k - 1][b] + move + offRoadCost(graph, to, gps[k])
      if (score < scores[k][c]) {
        scores[k][c] = score
        backs[k][c] = b
      }
    }
  }
}

// 후보 점이 GPS 점에서 떨어진 만큼의 값 (찻길이 없어 GPS 그대로 쓰는 후보는 0)
function offRoadCost(graph, node, gpsPoint) {
  if (node === NO_ROAD) return 0
  return Math.hypot(graph.xs[node] - gpsPoint.x, graph.zs[node] - gpsPoint.z) * GPS_WEIGHT
}

// (x, z)에서 SNAP_M 안의 찻길 점을 가까운 순으로 MAX_CANDIDATES개까지 (서로 CANDIDATE_GAP_M 넘게 떨어진 것만). 없으면 [NO_ROAD]
function candidatesNear(graph, x, z) {
  const near = []
  for (let node = 0; node < graph.xs.length; node++) {
    const meters = Math.hypot(graph.xs[node] - x, graph.zs[node] - z)
    if (meters <= SNAP_M) near.push({ node: node, meters: meters })
  }
  near.sort((a, b) => a.meters - b.meters) // 가까운 순 (람다: 두 개를 받아 비교하는 짧은 함수)
  const picked = []
  for (const item of near) {
    if (picked.length >= MAX_CANDIDATES) break
    let isApart = true
    for (const node of picked) {
      if (Math.hypot(graph.xs[node] - graph.xs[item.node], graph.zs[node] - graph.zs[item.node]) < CANDIDATE_GAP_M) isApart = false
    }
    if (isApart) picked.push(item.node)
  }
  return picked.length > 0 ? picked : [NO_ROAD]
}

// from에서 to까지 찻길을 따라 가장 짧은 길 (점 번호 목록, 첫 점 = from). 이어지지 않으면 null
function pathBetween(graph, from, to) {
  const straight = Math.hypot(graph.xs[from] - graph.xs[to], graph.zs[from] - graph.zs[to])
  const reach = spread(graph, from, (straight + SNAP_M * 2) * DETOUR_TIMES + DETOUR_EXTRA_M)
  if (!reach.costs.has(to)) return null
  const path = [to]
  while (path[0] !== from) path.unshift(reach.before.get(path[0])) // 도착점에서 출발점까지 거꾸로 되짚음
  return path
}

// items에서 from+1 ~ to번 자리를 지움. 그 자리에 있던 anchors는 from으로, 뒤쪽 anchors는 지운 만큼 앞으로
function cutItems(items, anchors, from, to) {
  items.splice(from + 1, to - from)
  for (const anchor of anchors) {
    if (anchor.at > to) anchor.at -= to - from
    else if (anchor.at > from) anchor.at = from
  }
}

// 같은 찻길 점을 다시 지나가면 그 사이(갔다가 되돌아온 곳)를 지움. LOOP_MAX_M보다 긴 것은 진짜로 돌아온 길일 수 있어 둠
function removeLoops(items, anchors) {
  let i = 0
  while (i < items.length) {
    let meters = 0
    let cutFrom = -1
    for (let back = i - 1; back >= 0 && cutFrom < 0; back--) { // i번 자리에서 뒤로 가며 같은 점을 찾음
      meters += Math.hypot(items[back + 1].x - items[back].x, items[back + 1].z - items[back].z)
      if (meters > LOOP_MAX_M) break
      if (items[i].node !== NO_ROAD && items[back].node === items[i].node) cutFrom = back
    }
    if (cutFrom < 0) {
      i++
      continue
    }
    cutItems(items, anchors, cutFrom, i)
    i = cutFrom + 1
  }
}

// 뒤로 꺾인 점(오던 방향과 거의 반대로 나가는 점)을 지움. 지우면 앞뒤가 다시 꺾일 수 있어 한 칸 물러나서 다시 봄
function removeSpikes(items, anchors) {
  let i = 1
  while (i < items.length - 1) {
    const inX = items[i].x - items[i - 1].x, inZ = items[i].z - items[i - 1].z
    const outX = items[i + 1].x - items[i].x, outZ = items[i + 1].z - items[i].z
    const lengths = Math.hypot(inX, inZ) * Math.hypot(outX, outZ)
    const isSpike = lengths > 0 && (inX * outX + inZ * outZ) / lengths < SPIKE_COS
    if (!isSpike) {
      i++
      continue
    }
    cutItems(items, anchors, i - 1, i)
    i = Math.max(1, i - 1)
  }
}

// anchors 사이의 자리들을 시각과 함께 넣음 (첫 자리 = 탄 점은 넣고, 마지막 자리 = 내린 점은 안 넣음)
function addTimedItems(result, items, anchors, mode) {
  let lastX = items[0].x, lastZ = items[0].z // 방금 넣은 자리
  const add = (item, minute) => {
    // 같은 자리에 서 있으면(정류장·신호) 1m 옆에 놓음: 같은 점이 이어지면 유니티가 경로 선을 못 그림
    const isSame = Math.hypot(item.x - lastX, item.z - lastZ) < 0.5
    lastX = item.x + (isSame ? 1 : 0)
    lastZ = item.z
    addPoint(result, lastX, lastZ, minute, mode, null)
  }
  addPoint(result, items[0].x, items[0].z, anchors[0].minute, mode, null)
  const lastIndex = items.length - 1
  for (let a = 1; a < anchors.length; a++) {
    const from = anchors[a - 1], to = anchors[a]
    if (to.at === from.at) {
      if (to.at < lastIndex) add(items[to.at], to.minute) // 그 자리에 서서 시간만 흐름
      continue
    }
    let total = 0 // from 자리에서 to 자리까지 길 따라 거리
    for (let n = from.at + 1; n <= to.at; n++) total += Math.hypot(items[n].x - items[n - 1].x, items[n].z - items[n - 1].z)
    let gone = 0
    for (let n = from.at + 1; n <= to.at; n++) {
      gone += Math.hypot(items[n].x - items[n - 1].x, items[n].z - items[n - 1].z)
      if (n === lastIndex) continue // 내린 점은 안 넣음
      add(items[n], from.minute + (to.minute - from.minute) * (total > 0 ? gone / total : 1))
    }
  }
}

// ---------- 찻길 그래프

// GPS 점들을 감싸는 네모 [작은 x, 작은 z, 큰 x, 큰 z] + GRAPH_MARGIN_M
function boxOfPoints(gps) {
  const box = [Infinity, Infinity, -Infinity, -Infinity]
  for (const point of gps) {
    box[0] = Math.min(box[0], point.x)
    box[1] = Math.min(box[1], point.z)
    box[2] = Math.max(box[2], point.x)
    box[3] = Math.max(box[3], point.z)
  }
  return [box[0] - GRAPH_MARGIN_M, box[1] - GRAPH_MARGIN_M, box[2] + GRAPH_MARGIN_M, box[3] + GRAPH_MARGIN_M]
}

// 네모 안의 찻길로 그래프 만들기. 돌려줌: { xs, zs (점 좌표), links (점마다 [이웃 점 번호, 거리(m), 비용]) }
function makeGraph(roads, box) {
  const graph = { xs: [], zs: [], links: [], corners: new Map() } // corners: 원래 꼭짓점을 칸(JOIN_M)에 담아 둔 것 (가까운 꼭짓점을 빨리 찾으려고)
  const isInBox = (x, z) => x >= box[0] && z >= box[1] && x <= box[2] && z <= box[3]
  for (const road of roads) {
    if (road.kind !== 0) continue // 찻길만
    const costTimes = road.width < MAIN_ROAD_M ? SMALL_ROAD_TIMES : 1
    let before = -1 // 바로 앞 꼭짓점의 점 번호
    for (let i = 0; i + 1 < road.points.length; i += 2) {
      const px = road.points[i], pz = road.points[i + 1]
      if (!isInBox(px, pz)) {
        before = -1
        continue
      }
      const corner = cornerNode(graph, px, pz)
      if (before >= 0 && before !== corner) linkAlong(graph, before, corner, costTimes)
      before = corner
    }
  }
  return graph
}

function newNode(graph, x, z) {
  graph.xs.push(x)
  graph.zs.push(z)
  graph.links.push([])
  return graph.xs.length - 1
}

// 길의 꼭짓점 → 점 번호. JOIN_M 안에 이미 있는 꼭짓점이 있으면 그 점을 같이 씀 (교차로에서 길들이 이어지게)
function cornerNode(graph, x, z) {
  const cellX = Math.floor(x / JOIN_M)
  const cellZ = Math.floor(z / JOIN_M)
  for (let dx = -1; dx <= 1; dx++) {
    for (let dz = -1; dz <= 1; dz++) {
      const list = graph.corners.get(cellX + dx + ',' + (cellZ + dz))
      if (!list) continue
      for (const node of list) {
        if (Math.hypot(graph.xs[node] - x, graph.zs[node] - z) <= JOIN_M) return node
      }
    }
  }
  const node = newNode(graph, x, z)
  const key = cellX + ',' + cellZ
  if (!graph.corners.has(key)) graph.corners.set(key, [])
  graph.corners.get(key).push(node)
  return node
}

// 두 꼭짓점 사이를 NODE_STEP_M마다 새 점으로 나눠 이음 (사이 점은 다른 길과 이어지지 않음: 고가도로 밑을 지나는 길과 붙으면 안 돼서)
function linkAlong(graph, from, to, costTimes) {
  const length = Math.hypot(graph.xs[to] - graph.xs[from], graph.zs[to] - graph.zs[from])
  const pieces = Math.max(1, Math.ceil(length / NODE_STEP_M))
  let before = from
  for (let piece = 1; piece <= pieces; piece++) {
    const rate = piece / pieces
    const node = piece === pieces ? to : newNode(graph, graph.xs[from] + (graph.xs[to] - graph.xs[from]) * rate, graph.zs[from] + (graph.zs[to] - graph.zs[from]) * rate)
    const meters = length / pieces
    graph.links[before].push([node, meters, meters * costTimes])
    graph.links[node].push([before, meters, meters * costTimes])
    before = node
  }
}

// ---------- 가장 짧은 길 (다익스트라)

/**
 * from 점에서 찻길을 따라 비용 limit 안에 갈 수 있는 모든 점까지의 가장 싼 비용
 * 돌려줌: { costs: Map(점 → 비용), before: Map(점 → 그 점으로 오기 바로 전 점, 길을 거꾸로 되짚을 때 씀) }
 */
function spread(graph, from, limit) {
  const costs = new Map([[from, 0]]) // 점 → 출발점에서 온 비용 (지금까지 찾은 가장 작은 값)
  const before = new Map()
  const done = new Set()             // 비용이 확정된 점
  const open = [from]                // 비용은 알지만 아직 확정 안 한 점들
  while (open.length > 0) {
    let pick = 0 // open에서 비용이 가장 작은 점
    for (let k = 1; k < open.length; k++) {
      if (costs.get(open[k]) < costs.get(open[pick])) pick = k
    }
    const node = open[pick]
    open.splice(pick, 1)
    if (done.has(node)) continue
    done.add(node)
    for (const [next, , cost] of graph.links[node]) {
      const total = costs.get(node) + cost
      if (total > limit || done.has(next)) continue
      if (!costs.has(next) || total < costs.get(next)) {
        costs.set(next, total)
        before.set(next, node)
        open.push(next)
      }
    }
  }
  return { costs: costs, before: before }
}
