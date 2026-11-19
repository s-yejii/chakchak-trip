// 테마로 떠나기 (③). 홈·로그인 전 홈의 동그라미와 테마 화면(S04-d)이 써요
// 11/13: 예전엔 방송 프로그램 이름(전참시·또간집 등)으로 7개를 두고 누르면 전부 "준비 중"이었어요.
//   이제 우리 DB에 장소가 충분한 진짜 코스 3개만 두고, 누르면 지역·종류가 채워진 채 AI 일정 만들기로 바로 시작해요
//   label: 동그라미 아래 이름 / img: 동그라미 사진 (public/img) / area: 여행 정보의 지역 / kinds·tags: "어디 위주로"·"이런 곳" 코드
//   kindText: 이 코스에 들어가는 곳 (kinds를 사람 말로, 테마 화면에 보여 줌)
//   11/14 디자인 검토: HOT·NEW 딱지는 근거 데이터가 없어서 뺐어요
//   (DB 장소 수 11/13 확인: 익선동 51 · 을지로 63 · 성수 180)
//   11/19 피드백: "노포·힙지로" 같은 AI 같은 말 → 쉬운 말로, 테마 화면의 긴 설명 줄은 뺌

export const themeList = [
  {
    id: 'ikseon', label: '익선동 골목', title: '익선동 한옥 골목 걷기', tagline: '한옥 골목의 밥집·카페 들르기', img: '/img/alley.jpg', kindText: '밥집 · 카페 · 전시',
    area: { name: '익선동', fullName: '서울 종로구 익선동', type: 'dong', lat: 37.5741, lng: 126.9901 },
    kinds: ['food', 'cafe', 'culture'], tags: ['mood'],
  },
  {
    id: 'euljiro', label: '을지로 노포', title: '을지로 오래된 맛집 돌기', tagline: '오래된 밥집·술집에서 저녁 먹기', img: '/img/euljiro.jpg', kindText: '밥집 · 술집 · 카페',
    area: { name: '을지로동', fullName: '서울 중구 을지로동', type: 'dong', lat: 37.5663, lng: 126.9919 },
    kinds: ['food', 'bar', 'cafe'], tags: [],
  },
  {
    id: 'seongsu', label: '성수 카페', title: '성수 카페·옷가게 거리', tagline: '카페와 옷가게를 번갈아 들르기', img: '/img/cafe.jpg', kindText: '카페 · 편집숍 · 밥집',
    area: { name: '성수동', fullName: '서울 성동구 성수동', type: 'dong', lat: 37.5446, lng: 127.0557 },
    kinds: ['cafe', 'shop', 'food'], tags: ['mood'],
  },
]

// id로 테마 하나 찾기 (없으면 null. 예전 테마 주소로 와도 화면이 깨지지 않게 테마 목록을 보여 줘요)
export function findTheme(id) {
  return themeList.find((theme) => theme.id === id) || null
}
