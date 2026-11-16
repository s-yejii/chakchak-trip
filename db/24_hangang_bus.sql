-- 24_hangang_bus.sql — 한강버스 선착장 8곳(retrip_place) + 운항 시간표(hangang_bus_time) (11/15 ① 이상재)
--  시간표: 한강버스 공식 운항 시간표 이미지(적용기간 26.8.15~별도 공지시까지)를 그대로 옮김. 표의 시각은 각 선착장 출발 시각
--  좌표·주소: 카카오 장소 검색 '한강버스 ○○선착장' (11/15). 옥수-압구정 직항은 없음 (한 배는 압구정이나 옥수 중 한 곳만 섬)
--  쓰는 곳: AI에게 묻기(④)에서 '한강버스 타도 예약에 안 늦는지' 계산. 시간표가 바뀌면 이 파일의 시각만 고쳐 다시 실행
--  예: 옥수 13:13 잠실행 배가 잠실에 몇 시 도착? = 같은 route·direction·run_no의 잠실 is_arrival 줄 (14:03)
--  12/1부터 겨울 시간표로 바뀔 수 있음 → 바뀌면 TIMES만 고쳐 다시 만들기
--  다시 실행해도 같은 결과 (장소는 ON CONFLICT, 시간표는 지우고 다시 넣기)
\encoding UTF8

BEGIN;

CREATE TABLE IF NOT EXISTS hangang_bus_time (
  pier_id    TEXT NOT NULL REFERENCES retrip_place(id) ON DELETE CASCADE,  -- 선착장 (hangang-oksu 등)
  route      TEXT NOT NULL,      -- '동부'(여의도~잠실) / '서부'(마곡~여의도). 여의도에서 갈아탐
  direction  TEXT NOT NULL,      -- '잠실행' / '여의도행' / '마곡행'
  run_no     SMALLINT NOT NULL,  -- 시간표의 몇 번째 줄 (route·direction·run_no가 같으면 같은 배)
  time_min   INT NOT NULL,       -- 자정부터 분 (13:13 → 793)
  is_arrival BOOLEAN NOT NULL DEFAULT FALSE,  -- 종점(마곡·잠실) 도착 시각이면 TRUE, 나머지는 출발 시각
  PRIMARY KEY (route, direction, run_no, pier_id, is_arrival)
);

-- 선착장 8곳: 장소로 넣어서 AI에게 묻기·지도에서 찾을 수 있게. 영업시간 = 그 선착장 첫 배~마지막 배 출발
INSERT INTO retrip_place (id,source,source_id,name,category,content_type_id,address,longitude,latitude,use_time,biz_type,place_group,map_url,overview,created_at,updated_at) VALUES
('hangang-magok','hangang','234547045','한강버스 마곡선착장','PLAY','12','서울특별시 강서구 가양동 441',126.84400626605535,37.575061778217076,'11:20~18:40 (요일별: 매일 11:20-18:40)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234547045','한강을 오가는 수상 버스 한강버스의 마곡 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-mangwon','hangang','234611728','한강버스 망원선착장','PLAY','12','서울특별시 마포구 마포나루길 420',126.89474985972166,37.55468784545127,'11:42~21:18 (요일별: 매일 11:42-21:18)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234611728','한강을 오가는 수상 버스 한강버스의 망원 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-yeouido','hangang','234669466','한강버스 여의도선착장','PLAY','12','서울특별시 영등포구 여의도동 85-1',126.93439744866133,37.528882169655944,'12:12~20:55 (요일별: 매일 12:12-20:55)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234669466','한강을 오가는 수상 버스 한강버스의 여의도 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-apgujeong','hangang','234726342','한강버스 압구정선착장','PLAY','12','서울특별시 강남구 압구정로11길 37-29',127.01647963172924,37.526686379656766,'12:55~20:37 (요일별: 매일 12:55-20:37)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234726342','한강을 오가는 수상 버스 한강버스의 압구정 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-oksu','hangang','234489151','한강버스 옥수선착장','PLAY','12','서울특별시 성동구 이촌한강공원길 792',127.01786950750945,37.538855999436464,'11:52~19:42 (요일별: 매일 11:52-19:42)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234489151','한강을 오가는 수상 버스 한강버스의 옥수 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-seoulforest','hangang','1604094566','한강버스 서울숲선착장','PLAY','12','서울특별시 성동구 성수동1가 697',127.04111182474081,37.53776088589692,'11:35~20:58 (요일별: 매일 11:35-20:58)','한강버스 선착장','관광·문화','https://place.map.kakao.com/1604094566','한강을 오가는 수상 버스 한강버스의 서울숲 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-ttukseom','hangang','234443646','한강버스 뚝섬선착장','PLAY','12','서울특별시 광진구 자양동 126-1',127.0664951037918,37.52880508535853,'11:17~21:16 (요일별: 매일 11:17-21:16)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234443646','한강을 오가는 수상 버스 한강버스의 뚝섬 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z'),
('hangang-jamsil','hangang','234660016','한강버스 잠실선착장','PLAY','12','서울특별시 송파구 잠실동 1-2',127.08479426715436,37.51871205060492,'11:00~19:20 (요일별: 매일 11:00-19:20)','한강버스 선착장','관광·문화','https://place.map.kakao.com/234660016','한강을 오가는 수상 버스 한강버스의 잠실 선착장. 마곡·망원·여의도·압구정·옥수·서울숲·뚝섬·잠실 8곳을 잇고, 여의도에서 갈아타요. 운항 시각은 시간표(hangang_bus_time)를 봐요.','2026-11-15T00:00:00.000Z','2026-11-15T00:00:00.000Z')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, address = EXCLUDED.address, longitude = EXCLUDED.longitude,
  latitude = EXCLUDED.latitude, use_time = EXCLUDED.use_time, biz_type = EXCLUDED.biz_type, overview = EXCLUDED.overview, updated_at = EXCLUDED.updated_at;

-- 운항 시간표: 지우고 다시 넣기
DELETE FROM hangang_bus_time WHERE pier_id LIKE 'hangang-%';
INSERT INTO hangang_bus_time (route, pier_id, direction, run_no, time_min, is_arrival) VALUES
('서부','hangang-magok','여의도행',1,680,FALSE),  -- 11:20
('서부','hangang-magok','여의도행',2,740,FALSE),  -- 12:20
('서부','hangang-magok','여의도행',3,800,FALSE),  -- 13:20
('서부','hangang-magok','여의도행',4,860,FALSE),  -- 14:20
('서부','hangang-magok','여의도행',5,900,FALSE),  -- 15:00
('서부','hangang-magok','여의도행',6,930,FALSE),  -- 15:30
('서부','hangang-magok','여의도행',7,960,FALSE),  -- 16:00
('서부','hangang-magok','여의도행',8,1020,FALSE),  -- 17:00
('서부','hangang-magok','여의도행',9,1060,FALSE),  -- 17:40
('서부','hangang-magok','여의도행',10,1120,FALSE),  -- 18:40
('서부','hangang-magok','마곡행',1,773,TRUE),  -- 12:53
('서부','hangang-magok','마곡행',2,833,TRUE),  -- 13:53
('서부','hangang-magok','마곡행',3,893,TRUE),  -- 14:53
('서부','hangang-magok','마곡행',4,953,TRUE),  -- 15:53
('서부','hangang-magok','마곡행',7,1053,TRUE),  -- 17:33
('서부','hangang-magok','마곡행',8,1113,TRUE),  -- 18:33
('서부','hangang-magok','마곡행',9,1153,TRUE),  -- 19:13
('서부','hangang-magok','마곡행',10,1213,TRUE),  -- 20:13
('서부','hangang-magok','마곡행',11,1269,TRUE),  -- 21:09
('서부','hangang-magok','마곡행',12,1299,TRUE),  -- 21:39
('서부','hangang-mangwon','여의도행',1,702,FALSE),  -- 11:42
('서부','hangang-mangwon','여의도행',2,762,FALSE),  -- 12:42
('서부','hangang-mangwon','여의도행',3,822,FALSE),  -- 13:42
('서부','hangang-mangwon','여의도행',4,882,FALSE),  -- 14:42
('서부','hangang-mangwon','여의도행',5,922,FALSE),  -- 15:22
('서부','hangang-mangwon','여의도행',6,952,FALSE),  -- 15:52
('서부','hangang-mangwon','여의도행',7,982,FALSE),  -- 16:22
('서부','hangang-mangwon','여의도행',8,1042,FALSE),  -- 17:22
('서부','hangang-mangwon','여의도행',9,1082,FALSE),  -- 18:02
('서부','hangang-mangwon','여의도행',10,1142,FALSE),  -- 19:02
('서부','hangang-mangwon','마곡행',1,754,FALSE),  -- 12:34
('서부','hangang-mangwon','마곡행',2,814,FALSE),  -- 13:34
('서부','hangang-mangwon','마곡행',3,874,FALSE),  -- 14:34
('서부','hangang-mangwon','마곡행',4,934,FALSE),  -- 15:34
('서부','hangang-mangwon','마곡행',7,1034,FALSE),  -- 17:14
('서부','hangang-mangwon','마곡행',8,1094,FALSE),  -- 18:14
('서부','hangang-mangwon','마곡행',9,1134,FALSE),  -- 18:54
('서부','hangang-mangwon','마곡행',10,1194,FALSE),  -- 19:54
('서부','hangang-mangwon','마곡행',11,1248,FALSE),  -- 20:48
('서부','hangang-mangwon','마곡행',12,1278,FALSE),  -- 21:18
('서부','hangang-yeouido','마곡행',1,732,FALSE),  -- 12:12
('서부','hangang-yeouido','마곡행',2,792,FALSE),  -- 13:12
('서부','hangang-yeouido','마곡행',3,852,FALSE),  -- 14:12
('서부','hangang-yeouido','마곡행',4,912,FALSE),  -- 15:12
('서부','hangang-yeouido','마곡행',7,1012,FALSE),  -- 16:52
('서부','hangang-yeouido','마곡행',8,1072,FALSE),  -- 17:52
('서부','hangang-yeouido','마곡행',9,1112,FALSE),  -- 18:32
('서부','hangang-yeouido','마곡행',10,1172,FALSE),  -- 19:32
('서부','hangang-yeouido','마곡행',11,1225,FALSE),  -- 20:25
('서부','hangang-yeouido','마곡행',12,1255,FALSE),  -- 20:55
('동부','hangang-yeouido','잠실행',1,758,FALSE),  -- 12:38
('동부','hangang-yeouido','잠실행',2,815,FALSE),  -- 13:35
('동부','hangang-yeouido','잠실행',3,880,FALSE),  -- 14:40
('동부','hangang-yeouido','잠실행',4,940,FALSE),  -- 15:40
('동부','hangang-yeouido','잠실행',5,960,FALSE),  -- 16:00
('동부','hangang-yeouido','잠실행',6,990,FALSE),  -- 16:30
('동부','hangang-yeouido','잠실행',7,1028,FALSE),  -- 17:08
('동부','hangang-yeouido','잠실행',8,1085,FALSE),  -- 18:05
('동부','hangang-yeouido','잠실행',9,1145,FALSE),  -- 19:05
('동부','hangang-yeouido','잠실행',10,1205,FALSE),  -- 20:05
('동부','hangang-apgujeong','여의도행',2,775,FALSE),  -- 12:55
('동부','hangang-apgujeong','여의도행',4,895,FALSE),  -- 14:55
('동부','hangang-apgujeong','여의도행',8,1045,FALSE),  -- 17:25
('동부','hangang-apgujeong','여의도행',10,1165,FALSE),  -- 19:25
('동부','hangang-apgujeong','여의도행',12,1215,FALSE),  -- 20:15
('동부','hangang-apgujeong','잠실행',2,847,FALSE),  -- 14:07
('동부','hangang-apgujeong','잠실행',4,972,FALSE),  -- 16:12
('동부','hangang-apgujeong','잠실행',6,1022,FALSE),  -- 17:02
('동부','hangang-apgujeong','잠실행',8,1117,FALSE),  -- 18:37
('동부','hangang-apgujeong','잠실행',10,1237,FALSE),  -- 20:37
('동부','hangang-oksu','여의도행',1,712,FALSE),  -- 11:52
('동부','hangang-oksu','여의도행',3,832,FALSE),  -- 13:52
('동부','hangang-oksu','여의도행',7,982,FALSE),  -- 16:22
('동부','hangang-oksu','여의도행',9,1102,FALSE),  -- 18:22
('동부','hangang-oksu','여의도행',11,1182,FALSE),  -- 19:42
('동부','hangang-oksu','잠실행',1,793,FALSE),  -- 13:13
('동부','hangang-oksu','잠실행',3,915,FALSE),  -- 15:15
('동부','hangang-oksu','잠실행',5,995,FALSE),  -- 16:35
('동부','hangang-oksu','잠실행',7,1063,FALSE),  -- 17:43
('동부','hangang-oksu','잠실행',9,1180,FALSE),  -- 19:40
('동부','hangang-seoulforest','여의도행',1,695,FALSE),  -- 11:35
('동부','hangang-seoulforest','여의도행',2,755,FALSE),  -- 12:35
('동부','hangang-seoulforest','여의도행',3,815,FALSE),  -- 13:35
('동부','hangang-seoulforest','여의도행',4,875,FALSE),  -- 14:35
('동부','hangang-seoulforest','여의도행',7,965,FALSE),  -- 16:05
('동부','hangang-seoulforest','여의도행',8,1025,FALSE),  -- 17:05
('동부','hangang-seoulforest','여의도행',9,1085,FALSE),  -- 18:05
('동부','hangang-seoulforest','여의도행',10,1145,FALSE),  -- 19:05
('동부','hangang-seoulforest','여의도행',11,1165,FALSE),  -- 19:25
('동부','hangang-seoulforest','여의도행',12,1195,FALSE),  -- 19:55
('동부','hangang-seoulforest','잠실행',1,811,FALSE),  -- 13:31
('동부','hangang-seoulforest','잠실행',2,868,FALSE),  -- 14:28
('동부','hangang-seoulforest','잠실행',3,933,FALSE),  -- 15:33
('동부','hangang-seoulforest','잠실행',4,993,FALSE),  -- 16:33
('동부','hangang-seoulforest','잠실행',5,1013,FALSE),  -- 16:53
('동부','hangang-seoulforest','잠실행',6,1043,FALSE),  -- 17:23
('동부','hangang-seoulforest','잠실행',7,1081,FALSE),  -- 18:01
('동부','hangang-seoulforest','잠실행',8,1138,FALSE),  -- 18:58
('동부','hangang-seoulforest','잠실행',9,1198,FALSE),  -- 19:58
('동부','hangang-seoulforest','잠실행',10,1258,FALSE),  -- 20:58
('동부','hangang-ttukseom','여의도행',1,677,FALSE),  -- 11:17
('동부','hangang-ttukseom','여의도행',2,737,FALSE),  -- 12:17
('동부','hangang-ttukseom','여의도행',3,797,FALSE),  -- 13:17
('동부','hangang-ttukseom','여의도행',4,857,FALSE),  -- 14:17
('동부','hangang-ttukseom','여의도행',7,947,FALSE),  -- 15:47
('동부','hangang-ttukseom','여의도행',8,1007,FALSE),  -- 16:47
('동부','hangang-ttukseom','여의도행',9,1067,FALSE),  -- 17:47
('동부','hangang-ttukseom','여의도행',10,1127,FALSE),  -- 18:47
('동부','hangang-ttukseom','여의도행',11,1147,FALSE),  -- 19:07
('동부','hangang-ttukseom','여의도행',12,1177,FALSE),  -- 19:37
('동부','hangang-ttukseom','잠실행',1,829,FALSE),  -- 13:49
('동부','hangang-ttukseom','잠실행',2,886,FALSE),  -- 14:46
('동부','hangang-ttukseom','잠실행',3,951,FALSE),  -- 15:51
('동부','hangang-ttukseom','잠실행',4,1011,FALSE),  -- 16:51
('동부','hangang-ttukseom','잠실행',5,1031,FALSE),  -- 17:11
('동부','hangang-ttukseom','잠실행',6,1061,FALSE),  -- 17:41
('동부','hangang-ttukseom','잠실행',7,1099,FALSE),  -- 18:19
('동부','hangang-ttukseom','잠실행',8,1156,FALSE),  -- 19:16
('동부','hangang-ttukseom','잠실행',9,1216,FALSE),  -- 20:16
('동부','hangang-ttukseom','잠실행',10,1276,FALSE),  -- 21:16
('동부','hangang-jamsil','여의도행',1,660,FALSE),  -- 11:00
('동부','hangang-jamsil','여의도행',2,720,FALSE),  -- 12:00
('동부','hangang-jamsil','여의도행',3,780,FALSE),  -- 13:00
('동부','hangang-jamsil','여의도행',4,840,FALSE),  -- 14:00
('동부','hangang-jamsil','여의도행',7,930,FALSE),  -- 15:30
('동부','hangang-jamsil','여의도행',8,990,FALSE),  -- 16:30
('동부','hangang-jamsil','여의도행',9,1050,FALSE),  -- 17:30
('동부','hangang-jamsil','여의도행',10,1110,FALSE),  -- 18:30
('동부','hangang-jamsil','여의도행',11,1130,FALSE),  -- 18:50
('동부','hangang-jamsil','여의도행',12,1160,FALSE),  -- 19:20
('동부','hangang-jamsil','잠실행',1,843,TRUE),  -- 14:03
('동부','hangang-jamsil','잠실행',2,900,TRUE),  -- 15:00
('동부','hangang-jamsil','잠실행',3,965,TRUE),  -- 16:05
('동부','hangang-jamsil','잠실행',4,1025,TRUE),  -- 17:05
('동부','hangang-jamsil','잠실행',5,1045,TRUE),  -- 17:25
('동부','hangang-jamsil','잠실행',6,1075,TRUE),  -- 17:55
('동부','hangang-jamsil','잠실행',7,1113,TRUE),  -- 18:33
('동부','hangang-jamsil','잠실행',8,1170,TRUE),  -- 19:30
('동부','hangang-jamsil','잠실행',9,1230,TRUE),  -- 20:30
('동부','hangang-jamsil','잠실행',10,1290,TRUE);  -- 21:30

COMMIT;
