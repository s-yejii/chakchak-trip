# 개발 환경 설정

팀원이 내 PC에서 개발할 때 보는 설명서입니다. 프로젝트 소개는 [README](../README.md)에 있습니다.

## 폴더 구조

```
tripbiseo/
├─ db/        PostgreSQL 스크립트 (번호 순서대로 실행)
├─ docker/    도커 DB 이미지(PostGIS + pgvector)와 처음 실행할 SQL 순서
├─ docs/      API.md(API 기준) · DB.md(테이블 기준) · ROLES.md(담당별 할 일) · DECISIONS.md · DEMO.md
├─ backend/   Spring Boot (Java 17)
│  └─ src/main/java/com/tripbiseo/
│     ├─ member/  ① 회원·홈·알림      ─┐
│     ├─ trip/    ② 여행·일정·장소      │ 기능마다 controller / service / mapper / dto
│     ├─ plan/    ③ AI 일정            │ (Service는 인터페이스 + Impl)
│     ├─ live/    ④ 여행 중            │
│     ├─ after/   ⑤ 여행 후·지도 부가  ─┘
│     ├─ common/  exception(오류) · auth(로그인 사용자) · ai(Gemini) · external(길찾기·날씨·장소 API) · util · dto
│     └─ config/  보안·Swagger·MyBatis 설정
│  └─ src/main/resources/mapper/{기능}/*.xml   SQL
├─ frontend/  Vue 3 + Vite — views(화면) / components(지도·시트·모달) / api(서버 호출) / router / utils
└─ unity/     3D 회고 유니티 프로젝트 (WebGL 빌드 결과는 frontend/public/unity3d/, 저장소 제외)
```

## 처음 실행

### 1. DB (도커)

평소 개발은 DB만 도커로 켜고, 서버·화면은 이클립스(STS)와 vite로 켭니다.

```
docker compose up -d db
```

- 처음 켤 때 `db/*.sql`이 `docker/db-init/00_init.sql` 순서대로 자동 실행됩니다 (몇 분 걸림).
- 진행 상황 보기: `docker compose logs -f db`
- DB 이미지가 바뀐 뒤: `docker compose up -d --build db` (데이터는 그대로 남음)
- 처음부터 다시: `docker compose down -v` (도커 DB의 데이터가 전부 지워짐)
- 포트는 **5433**입니다. 내 PC에 설치된 PostgreSQL(5432)과 같이 쓰기 위해서입니다.
- 테스트 계정: `tripuser` / `tripuser1234` (`db/90_test_data.sql`, 오늘 날짜 샘플 여행 포함)

| 파일 | 내용 |
|---|---|
| `01_common.sql` | PostGIS + 공통 테이블 (member, trip, trip_stop, retrip_place, retrip_review) |
| `02_retrip_schema.sql` | 결제·테마 테이블 |
| `03` ~ `10b`, `21` ~ `24` | 장소·리뷰·관광 데이터와 정리 (큰 파일이 있어 몇 분 걸림) |
| `11_member.sql` ~ `20_review_rag.sql` | 담당별 테이블 |
| `90_test_data.sql` | 테스트 계정 + 샘플 여행 |
| `95`, `96` | 시연용 데이터·계정 |
| `91_drop_old.sql` | 예전 DB의 안 쓰는 테이블 정리 (자동 실행에서 빠짐) |

리뷰 임베딩(`docker/db-init/emb.sql`)은 크기가 커서 저장소에 없습니다. 팀 카톡으로 받아 같은 위치에 두고 DB를 처음 켜면 같이 들어갑니다.

### 2. 내 PC 설정 (DB 비밀번호·API 키)

`backend/src/main/resources/application-local.properties.example`을 같은 폴더에 **`application-local.properties`** 로 복사하고 값을 채웁니다.

- 도커 DB를 쓰면 DB 주소를 `jdbc:postgresql://localhost:5433/tripbiseo`로 적습니다.
- 이 파일은 `.gitignore`에 있어서 커밋되지 않습니다. 키·비밀번호는 여기(또는 환경변수)에만 적습니다.
- 메모장으로 저장할 때 인코딩은 **UTF-8(BOM 없음)** 으로 합니다. BOM이 붙으면 첫 줄을 못 읽습니다.
- 이름 뒤에 `.txt`가 붙지 않았는지 확인합니다 (파일 탐색기 → 보기 → 파일 확장명).
- 같은 이름의 환경변수가 있으면 환경변수가 먼저입니다. 키를 바꿨는데 반영이 안 되면 Windows 환경변수에 예전 키가 남아 있는지 확인합니다.

### 3. 백엔드

이클립스에서 `backend` 폴더를 Maven 프로젝트로 가져와 `TripbiseoApplication`을 실행하거나, `backend` 폴더에서:

```
.\mvnw.cmd spring-boot:run
```

포트는 8080입니다. API 키가 하나도 없어도 서버는 켜집니다 (AI·길찾기는 규칙·추정 값으로 대신 동작).

- **Swagger (API 문서·시험 호출)**: http://localhost:8080/swagger-ui.html (로그인 없이 열림)
  로그인이 필요한 API는 Swagger에서 먼저 `POST /api/auth/login`을 실행하면 같은 브라우저 쿠키로 불러집니다.
  기준은 `docs/API.md`이고, Swagger와 다르면 API.md가 맞습니다.

### 4. 화면

`frontend` 폴더에서 아래를 실행한 뒤 http://localhost:5173 을 엽니다. `/api` 요청은 8080 서버로 넘어갑니다.

```
npm install
npm run dev
```

### 전부 도커로 켜기 (시연 PC·서버)

```
cp .env.example .env
docker compose --profile app up -d --build
```

`.env`에 DB 비밀번호와 API 키를 채웁니다 (`.env`는 커밋되지 않음). 브라우저에서 http://localhost (80번)를 엽니다.
구조는 `브라우저 → web(Nginx :80) → backend(Spring :8080) → db(:5432)`이고, 밖으로 열리는 것은 web 하나입니다.
끄기는 `docker compose --profile app down`입니다. 데이터와 올린 사진은 볼륨에 남습니다.

## API 키

`application-local.properties`(도커로 켤 때는 `.env`) 또는 환경변수에 아래 이름으로 넣습니다.

| 키 | 어디서 받나 | 없으면 |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | 내 DB 계정 | DB에 연결되지 않습니다 |
| `GEMINI_API_KEY` | Google AI Studio | AI 일정 만들기·AI 장소 검색·캡처 인식이 안 되고, 대안은 규칙으로만 만듭니다 |
| `GEMINI_MODEL` | (선택) | `gemini-3.8-flash` |
| `KAKAO_JS_KEY` | 카카오 콘솔 → 플랫폼 Web에 `http://localhost:5173` 등록 | 지도가 오픈스트리트맵으로 바뀝니다 |
| `KAKAO_REST_API_KEY` | 같은 카카오 앱의 REST 키 | DB에 없는 장소의 위치를 못 찾습니다 |
| `TMAP_APP_KEY` | SK open API (대중교통은 상품 신청이 필요) | ODsay로, 그것도 없으면 거리로 이동시간을 추정합니다 |
| `ODSAY_API_KEY` | ODsay LAB (서버 IP 등록) | TMAP이 실패했을 때 대신할 길찾기가 없습니다 |
| `TOUR_API_KEY` | 공공데이터포털 '한국관광공사_국문 관광정보 서비스_GW' (Decoding 키) | 기상청 키로 대신 부릅니다 |
| `KMA_SERVICE_KEY` | 공공데이터포털 '기상청_단기예보 조회서비스' (Decoding 키) | 날씨 알림·날씨 판단이 빠집니다 |

전체 키 이름은 `.env.example`에 있습니다.

## 예전 코드 보기

뼈대 전의 백엔드 코드(로직 참고용)는 `backend-v0` 태그에 있습니다. 예: `git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java`

## 알아 둘 것

- 웹 위치 찾기(내 위치)는 `localhost`나 https 주소에서만 됩니다. PC에서는 와이파이 기준의 대략적인 위치가 잡힙니다.
- 앱이 꺼져 있을 때 오는 푸시 알림은 아직 없습니다. 여행 중 화면이 열려 있는 동안만 알림이 옵니다.
- CSRF 보호는 팀 설정대로 꺼 두었습니다. 실제 서비스로 열기 전에 켜는 것을 검토해야 합니다.
- 네이버 장소·리뷰 데이터는 서울 프로토타입 시연용입니다 (약관상 자동 수집은 금지). 실제 서비스에서는 공식 API로 바꿔야 합니다.
- Gemini에는 개인정보·위치(좌표) 기록을 보내지 않습니다.
