-- 20_review_rag.sql — ④ 신예지 (AI에게 묻기 · 리뷰 RAG, 11/15)
--  "조용한", "작업하기 좋은" 같은 분위기 조건을 ILIKE 글자 대신 뜻으로 찾으려고 리뷰를 임베딩(숫자 배열)으로 저장해요
--  pgvector가 있는 DB에서만 실행돼요 (도커 DB: docker/db/Dockerfile). 없는 DB에서는 이 파일만 건너뛰면 되고, 서버는 예전 방식(ILIKE)으로 동작해요
--  임베딩 값은 SQL이 아니라 서버(관리용 API)가 Gemini로 만들어서 넣어요. 다시 실행해도 안전하게 (IF NOT EXISTS)

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------- review_embedding · 리뷰 하나의 임베딩
--  리뷰가 평균 71~140자로 짧아서 자르지 않고 리뷰 하나 = 한 줄
--  place_id는 retrip_review에 있으니 따로 두지 않고 JOIN으로 읽어요
CREATE TABLE IF NOT EXISTS review_embedding (
  review_id  TEXT PRIMARY KEY REFERENCES retrip_review(id) ON DELETE CASCADE,
  embedding  vector(768) NOT NULL,          -- Gemini 임베딩 768차원
  model      VARCHAR(60) NOT NULL,          -- 만든 모델 (모델이 바뀌면 다시 만들어야 해서 기록)
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 벡터 인덱스(HNSW)는 두지 않아요: 시연 동선 리뷰 약 1,830개(가게마다 긴 리뷰 6개)는 전부 비교해도 수 ms이고, 인덱스는 근사 검색이라 정확도가 조금 떨어져요.
-- 리뷰가 10만 개를 넘게 늘면 그때 추가: CREATE INDEX ON review_embedding USING hnsw (embedding vector_cosine_ops);

-- ---------------------------------------------------------------- ragadmin · 리뷰 RAG 관리 계정 (11/16)
--  /api/live/rag/embed·rank·compare(임베딩 채우기·평가)는 이 계정만 부를 수 있어요 (Gemini 한도를 아무나 쓰지 못하게)
--  관리 계정 목록은 RAG_ADMIN_ACCOUNTS (기본 ragadmin, ReviewRagServiceImpl). 96_demo_account.sql과 같은 방식
--  비밀번호 원문은 여기에 없어요. 팀 카톡으로만 공유해요 (아래는 BCrypt 암호문). 이미 있으면 아무것도 안 바꿔요
INSERT INTO member (username, password, nickname, role)
VALUES ('ragadmin', '$2a$10$J2wSEa54CHgs.lUP4KTMYeSUQUGNZUnM.YUpOGIMuqOkmFGSVQj26', 'RAG관리', 'ROLE_USER')
ON CONFLICT (username) DO NOTHING;
