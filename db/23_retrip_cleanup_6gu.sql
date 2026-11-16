-- 23_retrip_cleanup_6gu.sql — 21·22로 넣은 6개 구 데이터 정제 (11/15 ① 이상재). 21·22 다음에 실행, 다시 실행해도 같은 결과
\encoding UTF8

BEGIN;

-- 1. 네이버에서 사라지거나 폐업·이전 안내가 뜬 가게 18곳 삭제 (11/15 상재 결정). 일정 칸·시연 데이터 참조 0곳
--  리뷰·메뉴·영업시간은 같이 지워짐(ON DELETE CASCADE)
DELETE FROM retrip_place WHERE id IN (
  'naver-37565028',  -- 스타벅스 여의도역R점
  'naver-1811394752',  -- 냐옹지마
  'naver-2080886300',  -- 스시마우 연세세브란스빌딩점
  'naver-1327018569',  -- 학교앞분식
  'naver-1201043555',  -- 베이커리 앤 커피 by 쿤달 압구정로데오 플래그쉽스토어
  'naver-37996835',  -- 136길 육미
  'naver-318911151',  -- 김밥속으로 강남본점
  'naver-1996126498',  -- 떡밀밀 강남논현점
  'naver-1954673280',  -- 돈막촌
  'naver-1747102327',  -- 정성달아 한남동 본점
  'naver-1557216458',  -- 낭만짚불구이 약수점
  'naver-37629568',  -- 오토김밥 본점
  'naver-965428244',  -- 이이네
  'naver-326940526',  -- 한스크라상
  'naver-19407552',  -- 선비옥
  'naver-2078181505',  -- 제주아홉 혼술바 서울 잠실점
  'naver-1490832649',  -- 오브너리
  'naver-13576778'  -- 라크슌
);

-- 2. 위와 같지만 일정 칸(trip_stop)이나 시연 데이터가 쓰는 4곳은 지우지 않고 폐업 표시만
--  지우면 일정 칸의 장소 연결이 비어서(ON DELETE SET NULL) 시연 일정이 바뀔 수 있음
UPDATE retrip_place SET biz_status = '폐업', status_checked_at = '2026-11-15T12:11:35.000Z' WHERE id IN (
  'naver-1081287927',  -- 란 이자카야
  'naver-37577974',  -- 봉우이층집
  'naver-11609540',  -- 봉우화로
  'naver-1450180486'  -- 치솟 역삼본점
);

-- 3. 이미 DB에 있던 애매 업종(호두과자·테마카페 등) 1곳 삭제 (08 F-3과 같은 기준)
DELETE FROM retrip_place WHERE id IN (
  'naver-2027190763'  -- 복호두 여의도점
);

-- 4. 스시이세이(2일차 B안 점심): 주소 끝에 가게 이름이 붙어 들어온 것 고치기. 에비뉴엘 잠실점도 올림픽로 300(롯데월드몰 단지) 안
UPDATE retrip_place SET address = regexp_replace(address, '\s*스시이세이 잠실 롯데월드몰점$', '')
WHERE id = 'naver-1160275656';
--  업종은 네이버 "초밥,롤" 대신 일식당으로 (11/15 상재 결정, 시나리오 화면에 일식당으로 보이게)
UPDATE retrip_place SET biz_type = '일식당' WHERE id = 'naver-1160275656';
--  11/15 상재: 시연 시나리오 맞춤 (실제 가게는 15:00 브레이크, 촬영 전 전화 확인). 브레이크 16:00~17:30, 14:30~16:00 점심 → 16:40 동서울터미널 버스
--  use_time 글("11:00~22:00 (요일별: 매일 11:00-22:00)")에는 브레이크가 없어서 그대로
UPDATE place_hours SET break_start = 960 WHERE place_id = 'naver-1160275656';

-- 5. 08_retrip_cleanup.sql의 규칙을 새 데이터에 다시 적용 (모두 글자 규칙이라 다시 실행해도 같은 결과)

-- 편의시설(amenities) 정리 (11/06 ① 이상재): 쉼표로 나눈 값 중 아래 목록에 없는 것은 뺌 (예: 시그니처버거·리저브)
--  목록은 네이버 지도 편의시설 이름 그대로. 장소 상세 화면이 쉼표로 나눠 보여 줘서, 목록 밖 글자는 이상하게 보임
--  다시 실행해도 같은 결과 (목록 밖 값이 있는 장소만 고침)
UPDATE retrip_place p SET amenities = (
    SELECT string_agg(TRIM(a.item), ', ' ORDER BY a.ord)
    FROM unnest(string_to_array(p.amenities, ',')) WITH ORDINALITY AS a(item, ord)
    WHERE TRIM(a.item) IN ('무선 인터넷','단체 이용 가능','포장','예약','주차','남/녀 화장실 구분','간편결제','배달','유아의자','대기공간',
                           '반려동물 동반','발렛파킹','방문접수/출장','노키즈존','유아시설 (놀이방)','24시간','장애인 편의시설','드라이브스루'))
WHERE p.amenities IS NOT NULL
  AND EXISTS (SELECT 1 FROM unnest(string_to_array(p.amenities, ',')) AS b(item)
              WHERE TRIM(b.item) <> '' AND TRIM(b.item) NOT IN ('무선 인터넷','단체 이용 가능','포장','예약','주차','남/녀 화장실 구분','간편결제','배달','유아의자','대기공간',
                           '반려동물 동반','발렛파킹','방문접수/출장','노키즈존','유아시설 (놀이방)','24시간','장애인 편의시설','드라이브스루'));

-- 08 B와 같은 규칙: 날짜 붙은 하루 휴무 조각 지우기 (크롤링한 주의 임시 휴무). 21에서 넣은 네이버 가게만
UPDATE retrip_place SET use_time =
  regexp_replace(regexp_replace(regexp_replace(use_time,
    '\(요일별: [월화수목금토일]\([0-9]+/[0-9]+\) 휴무\)', '', 'g'),
    '[월화수목금토일]\([0-9]+/[0-9]+\) 휴무 / ', '', 'g'),
    ' / [월화수목금토일]\([0-9]+/[0-9]+\) 휴무', '', 'g')
WHERE use_time ~ '[월화수목금토일]\([0-9]+/[0-9]+\) 휴무' AND source = 'naver' AND updated_at = '2026-11-15T12:11:35.000Z';

-- F-2. 업종 칸에 가게 이름(맘스터치)이 들어간 26곳 → 햄버거
UPDATE retrip_place SET biz_type = '햄버거' WHERE biz_type = '맘스터치';

-- F-5. 주소 앞부분 통일: "서울 ○○구"·"서울시 ○○구" → "서울특별시 ○○구" (11/07 상재 결정)
--  코드 확인: 주소를 LIKE '서울%'(AreaStatMapper), split_part(address,' ',2)·split(" ")[1](구 이름), contains(구 이름)로만 써서 안 깨짐
--  (일정 만들기 region "서울 마포구"는 PlanCandidateFinder.addArea가 낱말로 나누고 "서울"을 빼서 "마포구"로 찾음)
--  다시 실행해도 같은 결과. 새로 넣은 데이터(내일 영등포 크롤링 '서울 ○○구')에도 다시 돌리기
UPDATE retrip_place SET address = regexp_replace(address, '^서울(시)?\s+', '서울특별시 ')
WHERE address ~ '^서울(시)?\s';

-- 가게마다 다름 → "점포별 상이" (새 데이터에도 맞게 글자 규칙으로)
UPDATE retrip_place SET rest_date = '점포별 상이'
WHERE rest_date ~ '^(※\s*)?(점포|매장)\s*(별로|별|마다)?\s*상이(함)?$';

-- F-8. 영업시간 속 "날짜 붙은 정기휴무(매달·격주)"와 "한글날 휴무" 조각 지우기 (11/07 상재 결정, 영등포 제외)
--  예: "수(10/14) 정기휴무 (매달 2, 4번째 수요일)", "일(10/11) 정기휴무 (격주 일요일)", "금(10/9) 한글날 휴무"
--  이유: 서버 OpeningHours.java가 날짜를 지우고(46줄) 그 요일 글에 "휴무"가 있으면 닫힘으로 읽음(124줄, 164줄)
--   → 한 달에 두 번 쉬는 곳·한글날 하루 쉰 곳이 매주 그 요일 휴무가 됨. 지운 요일은 "정보 없음"이 됨
--  지운 원문: C:\mylx\db-check\격주휴무-원문.csv (나중에 서버가 "매달 ○번째"를 읽게 고칠 때 씀)
--  다시 실행해도 같은 결과
UPDATE retrip_place SET use_time =
  regexp_replace(regexp_replace(regexp_replace(use_time,
    '\(요일별: [월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무)\)', '', 'g'),
    '[월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무) / ', '', 'g'),
    ' / [월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무)', '', 'g')
WHERE use_time ~ '[월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)|한글날 휴무)'
  AND address NOT LIKE '%영등포구%';

-- F-10. 관광공사 사진 주소 http → https (11/07 상재 결정). https로 배포하면 브라우저가 http 사진을 막아서(혼합 콘텐츠)
--  tong.visitkorea.or.kr는 https로도 열림 (11/07 확인). 다시 실행해도 같은 결과. 06을 다시 돌리면 이 블록도 다시
UPDATE retrip_place SET image_url = regexp_replace(image_url, '^http://', 'https://')
WHERE image_url LIKE 'http://%';

-- 메뉴 정리 (11/08 상재): 크롤러가 메뉴 화면의 "이전 페이지" 버튼 글자를 메뉴 이름으로 읽은 줄 (457곳에 1줄씩, 진짜 메뉴는 따로 있음)
DELETE FROM place_menu WHERE name = '이전 페이지';


COMMIT;

-- ===== 11/15 상재 2차 검수 (21·22·23을 넣은 뒤 DB 전체 다시 봄). 다시 실행해도 같은 결과 =====
--  지우는 장소는 모두 일정 칸(trip_stop)·내 장소(my_place) 참조 0곳 (11/15 확인)
--  리뷰·메뉴·영업시간·사진은 같이 지워짐(ON DELETE CASCADE), 일정 칸은 SET NULL
BEGIN;

-- 6. 08에서 "여행지가 아니라서" 지운 곳이 21(refresh)·22(관광 목록)로 다시 들어온 33곳 + 남산인권숲 → 다시 삭제
DELETE FROM retrip_place WHERE id IN (
  -- 08 F-4 (11/07 상재 결정: 여행지로 추천하기 어려움) 28곳
  'tour-3080721',  -- 김세중미술관
  'tour-3447859',  -- 남산인권숲
  'tour-1603543',  -- 무궁화동산
  'tour-2783339',  -- 별책부록
  'tour-231897',   -- 서울 삼전도비
  'tour-1603175',  -- 아시아공원
  'tour-337413',   -- 안양암(서울)
  'tour-754063',   -- 오금공원
  'tour-3080775',  -- 유니플렉스
  'tour-1829827',  -- 의열사(용산)
  'tour-3076665',  -- 조은숙아트앤라이프스타일
  'tour-482153',   -- 청룡사(서울)
  'tour-130263',   -- 초전섬유·퀼트박물관
  'tour-130226',   -- 포스코미술관
  'tour-1606001',  -- 해풍부원군윤택영댁재실
  'tour-2373204',  -- 경성 부민관 폭탄 의거지
  'tour-2752525',  -- 마루공원
  'tour-1957600',  -- 명동 남대문 북창동 다동무교동 관광특구
  'tour-2592885',  -- 엘크레
  'tour-2752545',  -- 역삼개나리공원
  'tour-250131',   -- 올림픽대교
  'tour-250134',   -- 율곡로
  'tour-3427223',  -- 충무로영상센터 오재미동
  'tour-822861',   -- 한강
  'tour-3080852',  -- 한생연 실험누리과학관
  'tour-3012716',  -- 한생연융합교육과학관
  'tour-2758809',  -- 한양공원
  'tour-1605966',  -- 훈련원공원
  -- 08 첫 정제(11/06)
  'tour-2751339',  -- KBS온
  -- 08 폐업 확인(11/06)·F-6
  'naver-1000464271',  -- 쿄코코 대학로점
  'naver-33877385',    -- 북경원
  -- 08 영등포 크롤링 확인(11/08): 일주일 내내 휴무, 임시 팝업
  'naver-20894091',    -- 문화포차
  'naver-1796129798'   -- 마사비스 더현대서울 팝업
);

-- 7. 좌표가 서울 밖(위도 19.69·경도 117.99)으로 들어온 관광공사 2곳 (08과 같은 문제). 좌표는 카카오 장소 검색 (11/15)
UPDATE retrip_place SET latitude = 37.53067071598, longitude = 126.953505666775 WHERE id = 'tour-2760249';     -- 성촌공원 (용산구 원효로4가)
UPDATE retrip_place SET latitude = 37.52226188885297, longitude = 127.10409031045577 WHERE id = 'tour-2606740'; -- 서울책보고 (송파구 신천동 14)

-- 8. 영업시간이 "일(11/15) 휴무" 한 조각뿐인 네이버 26곳: 크롤링한 날의 임시 휴무만 읽힘 → 영업시간 비움 (08 C와 같은 방식, 화면에 "영업시간 정보 없음")
UPDATE retrip_place SET use_time = NULL WHERE use_time = '일(11/15) 휴무' AND source = 'naver';
DELETE FROM place_hours h USING retrip_place p
WHERE p.id = h.place_id AND p.source = 'naver' AND p.use_time IS NULL
  AND NOT EXISTS (SELECT 1 FROM place_hours x WHERE x.place_id = h.place_id AND NOT x.closed);

-- 9. 같은 숙소 중복: 정스패밀리(용산, 41m). 사진 없는 쪽 삭제
DELETE FROM retrip_place WHERE id = 'tour-2568938';

-- 10. 메뉴가 아닌 줄 22줄 삭제 + 가짜 가격 1줄은 가격만 비움 (크롤러가 안내·공지·메뉴판 버튼 글을 메뉴로 읽음)
DELETE FROM place_menu WHERE (place_id, seq) IN (
  ('naver-1094224879',6), ('naver-1138288219',2), ('naver-1150149691',8), ('naver-11714851',7), ('naver-1180736872',6),
  ('naver-13156952',2), ('naver-13573522',13), ('naver-1358446173',13), ('naver-1529223205',2), ('naver-1546654146',20),
  ('naver-1603194284',3), ('naver-1678750949',6), ('naver-1684619726',5), ('naver-1723824304',1), ('naver-1760424099',6),
  ('naver-1774105337',7), ('naver-19873286',11), ('naver-2034114771',4), ('naver-2045605196',1), ('naver-2045605196',2),
  ('naver-2083276011',2), ('naver-32602489',2)
);
UPDATE place_menu SET price = NULL WHERE place_id = 'naver-1320828185' AND seq = 12 AND price = 9999999;  -- 직접만든 17가지 반찬 소스

-- 11. http 사진 3장: 가게 사진이 아니라 블로거 프로필 사진(blogpfthumb)이라 삭제
DELETE FROM place_image WHERE url LIKE 'http://blogpfthumb.%';

COMMIT;

-- ===== 11/15 상재 3차 검수: 22·21로 새로 들어온 곳 중 11/07 F-4 기준(표석·관광특구 이름·다리·작은 묘역)과 일주일 내내 휴무인 네이버 3곳 =====
--  모두 일정 칸·내 장소 참조 0곳. 전주이씨광평대군파묘역(tour-753954)은 문화재라 남김. 다시 실행해도 같은 결과
BEGIN;
DELETE FROM retrip_place WHERE id IN (
  'tour-2373223',      -- 대한제국군 서울시가 전투지 (표석)
  'tour-2373363',      -- 이재명 의거지 (표석)
  'tour-2373320',      -- 맥아더 사령관 한강방어선 시찰지 (표석)
  'tour-126999',       -- 이태원 관광특구 (관광특구 이름)
  'tour-1957671',      -- 종로 청계 관광특구 (관광특구 이름)
  'tour-753558',       -- 마포대교 (다리)
  'tour-754003',       -- 신선경과 류인호 묘역
  'naver-1260049730',  -- 한솔식당 (종로, 월~토 휴무 + 일 정기휴무)
  'naver-1600361099',  -- 월간국밥 (용산, 같은 모양)
  'naver-1989454530'   -- 애니떡볶이 (중구, 같은 모양)
);
COMMIT;

-- ===== 11/16 상재 4차 검수: 관광지 칸에 안 맞는 투어 장소 25곳. 다시 실행해도 같은 결과 =====
--  기준(11/16 상재): "점심-관광지-관광지-저녁"의 관광지 칸에 먹자골목·절·성당·열차·숙소 같은 곳은 안 됨
--  지우는 장소는 모두 일정 칸(trip_stop)·내 장소(my_place)·95/96 시드·DemoScenario 참조 0곳 (11/16 확인)
--  리뷰·메뉴·영업시간·사진은 같이 지워짐(ON DELETE CASCADE), 일정 칸은 SET NULL
BEGIN;

-- 11. 관광지 칸에 안 맞는 투어 장소 25곳
DELETE FROM retrip_place WHERE id IN (
  -- 먹자골목·음식거리 8곳 (밥 먹는 곳이라 관광지 칸에 넣으면 밥을 두 번 먹는 일정이 됨)
  'tour-699249',   -- 신당동 떡볶이타운
  'tour-699279',   -- 장충동 족발 골목
  'tour-748018',   -- 남대문 갈치조림골목
  'tour-749192',   -- 공덕동 족발골목
  'tour-704506',   -- 서울 삼각지 대구탕 골목
  'tour-2401757',  -- 자양동 양꼬치거리 (중국음식문화거리)
  'tour-2591805',  -- 국제음식문화거리(INTERNATIONAL FOOD STREET)
  'tour-2591826',  -- 음식문화특화거리(깔깔거리)
  -- 종교 시설 10곳 (절·성당·성지·사당)
  'tour-126505',   -- 승가사
  'tour-298109',   -- 금선사
  'tour-1718910',  -- 템플스테이 홍보관
  'tour-2764331',  -- 인왕산 국사당
  'tour-126522',   -- 서울 동관왕묘
  'tour-126528',   -- 절두산 순교성지
  'tour-126803',   -- 당고개순교성지
  'tour-126529',   -- 새남터기념성당
  'tour-127015',   -- 대한성공회 서울주교좌성당
  'tour-250330',   -- 서울 약현성당
  -- 열차·교통 4곳
  'tour-1987566',  -- 서해금빛열차
  'tour-2440925',  -- 레일크루즈 해랑열차
  'tour-2758150',  -- 용산역광장
  'tour-2758868',  -- 거북선나루터(이촌 수상훈련장)
  -- 숙소 같은 곳 2곳 (관광지로 들어와 있음)
  'tour-2767753',  -- 후암거실
  'tour-2932122',  -- 후암별채
  -- 체험 시설 1곳 (상재 결정)
  'tour-4153922'   -- 런닝맨 인사동점
);
COMMIT;

-- 11/16 ① 이상재: 영업시간이 빈 관광지 채우기 (② 찬기 요청). 빈 곳만 바꿔요 (다시 실행해도 같은 결과)
--  "상시 개방"이면 앱이 늘 열린 곳으로 계산해요 (OpeningHours). 북촌한옥마을 tour-126537의 10:00~17:00은 주민 보호 방문 시간이라 그대로
BEGIN;
UPDATE retrip_place SET use_time = '상시 개방'
WHERE id IN ('tour-2946228',                          -- 익선동 한옥거리
             'cf66f230-e665-57e2-a78a-6358da042de3',  -- 성수동 카페거리
             '7cfdfd7b-ea87-5ef5-ae6a-aa07397d0929',  -- 북촌한옥마을 (sample)
             'ce57766d-bb3b-5cc4-aa49-c8aee11034aa',  -- 인사동 문화의거리
             '9b0d5521-9ff2-52ec-a1ea-36e9114f63ad',  -- 광화문광장
             'tour-126480',                           -- 관악산
             'tour-3452906')                          -- 홍제천인공폭포
  AND (use_time IS NULL OR use_time = '');
-- 진짜 시간: 경복궁은 촬영일(11월) 기준 (앱은 첫 시간만 읽음), 석파정·무계원은 같은 곳의 다른 줄 값
UPDATE retrip_place SET use_time = '09:00~17:00 (11월~2월 기준, 입장마감 16:00)' WHERE id = 'e70caff5-27cf-5c76-a1f3-201b7032d672' AND (use_time IS NULL OR use_time = ''); -- 경복궁
UPDATE retrip_place SET use_time = '10:00~18:00 (입장마감 17:00)' WHERE id = 'tour-3449598' AND (use_time IS NULL OR use_time = ''); -- 석파정
UPDATE retrip_place SET use_time = '10:00~18:00' WHERE id = 'tour-3424542' AND (use_time IS NULL OR use_time = ''); -- 종로 무계원
COMMIT;
