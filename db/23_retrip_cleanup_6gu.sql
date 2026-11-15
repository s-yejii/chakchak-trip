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
