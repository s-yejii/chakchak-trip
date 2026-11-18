// 시연 가짜 GPS가 지금 따라 움직이는 길 (11/09 ①, 시연 피드백)
// 이동하는 동안 여행 중 지도의 남색 점선도 이 길 하나만 그려서, 점과 선이 항상 같은 길 위에 있게 해요.
// (서버 상황 응답의 길은 "지금 위치 → 다음 장소"로 매번 새로 계산돼서, 이동 중간에 받으면 다른 길로 바뀌어요)
// DemoDrive가 출발할 때 넣고, 도착하면 비워요. 값: [[위도, 경도], ...] 또는 null
import { ref } from 'vue'

export const demoRoute = ref(null)

// 시연 한강버스 타는 동안 여행 중 화면 맨 위 줄 (11/17 주형, ④ 예지 OK)
// 서버 계산 "다음 OO까지 대중교통 45분" 대신 "한강버스 타는 중 · 잠실 14:03 도착"을 보여 줘요. 값: { cap, big, strong } 또는 null
export const demoRide = ref(null)

// 출발 전 한강버스 카드(여행 중 화면)가 계산 중이거나 떠 있는지 (11/17 주형: 카드를 고르기 전에 [다음 장소로]로 그냥 떠나던 문제)
// HangangBusOption(출발 전 모양)이 켜고 끔. DemoDrive는 이게 true면 출발 안 함
export const boatPending = ref(false)
// 출발 전 한강버스 카드가 서버에 묻는 중인지 (11/17: 이때 [다음 장소로]를 누르면 막지 않고 대답을 몇 초 기다렸다가 판단)
export const boatChecking = ref(false)

// 장면 2 (11/17 주형): 순환버스에서 내린 뒤 다음 예약까지 시간이 많이 남으면 가운데 카드 "이대로 갈까요?"
// DemoDrive가 { name, slackMin }을 넣고 시계를 멈춤 → 여행 중 화면에서 고르면 null (그때 DemoDrive가 시계를 다시 흐르게)
export const restAsk = ref(null)
