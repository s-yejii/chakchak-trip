// ③ 하루에 다 갈 수 있는지 (11/20 피드백 "장소가 멀리 떨어져 있으면 며칠 필요한지 판단해 기간 늘려 주기")
// 계산은 서버 POST /api/plans/day-check (PostGIS ST_ClusterDBSCAN으로 1.5km 안끼리 동네 묶기 → 머무는 시간 + 걷기 + 동네 옮기기 어림)
// 여기는 담은 장소의 id만 기억해 뒀다가 서버에 물어요. 실패하면 null (그냥 진행)
import { reactive } from 'vue'
import { post } from '@/api/http'

// 담은 장소 이름 → 장소 id (WishLinks가 담을 때 넣고, 빼면 지움)
export const wishPlaces = reactive({})

/** 담은 장소들을 하루(dayMinutes분)에 다 갈 수 있는지 서버에 물어요. { groups, needMin, dayMin, stayMin, walkMin, moveMin, over } 또는 null */
export async function checkDay(names, dayMinutes) {
  const placeIds = []
  for (const name of names) {
    if (wishPlaces[name]) placeIds.push(wishPlaces[name])
  }
  if (placeIds.length < 2) return null
  try {
    return await post('/plans/day-check', { placeIds, dayMinutes })
  } catch {
    return null
  }
}
