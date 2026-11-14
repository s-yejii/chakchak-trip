# 착착트립 DB 이미지 = PostGIS 이미지 + pgvector (11/15 ④ 신예지)
#   PostGIS: 반경·가까운 순 검색 (지금까지 쓰던 것)
#   pgvector: 리뷰 임베딩(숫자 배열)을 저장하고 비슷한 리뷰를 찾는 확장 (리뷰 RAG)
#   postgis 이미지는 PostgreSQL 공식 apt 저장소가 이미 등록돼 있어서 패키지 이름만 적으면 설치돼요
FROM postgis/postgis:17-3.5

RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-17-pgvector \
 && rm -rf /var/lib/apt/lists/*
