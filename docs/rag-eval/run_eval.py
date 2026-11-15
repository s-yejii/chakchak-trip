"""
리뷰 RAG 평가 1단계: 질문 30개를 A(글자)·B(벡터)·C(RAG)로 돌리고, 정답 표시용 표를 만들어요. (④ 신예지, 11/15)

준비: 도커 DB와 백엔드 서버(8080)가 켜져 있어야 해요. 시연 동선 리뷰 임베딩이 채워져 있어야 B·C가 제대로 나와요.
실행 (프로젝트 폴더에서):  python docs/rag-eval/run_eval.py
결과:
  docs/rag-eval/results.json  질문마다 A·B·C가 고른 상위 5와 걸린 시간 (점수 계산에 씀)
  docs/rag-eval/labels.csv    A·B·C 상위 5를 모은 가게(풀링)마다 리뷰 → label 칸에 1(맞음)/0(아님)을 사람이 채움
                              어느 방식이 골랐는지는 일부러 안 보여 줘요 (채점이 한쪽으로 쏠리지 않게)
파이썬 기본 도구만 써요 (따로 설치할 것 없음).
"""
import csv
import http.cookiejar
import json
import os
import subprocess
import sys
import urllib.request

BASE_URL = os.environ.get("EVAL_BASE_URL", "http://localhost:8080")
USERNAME = os.environ.get("EVAL_USER", "tripuser")          # 90_test_data.sql 테스트 계정
PASSWORD = os.environ.get("EVAL_PASSWORD", "tripuser1234")
DB_CONTAINER = "tripbiseo-db"
HERE = os.path.dirname(os.path.abspath(__file__))
REVIEWS_PER_PLACE = 6       # 정답 표시할 때 보여 줄 리뷰 수
REVIEW_MAX_CHARS = 90

# 시연 장소 좌표 (plan/service/DemoScenario.java, 임베딩 범위 SQL과 같은 곳)
PLACES = {
    "종로3가/익선": (37.5732, 126.9898),
    "명동 숙소": (37.5616, 126.9827),
    "남산나들길": (37.5549, 126.9867),
    "팔각정": (37.5516, 126.9876),
    "예장공원": (37.5605, 126.9889),
    "을지로 회식": (37.5658, 126.9906),
    "강남 스시도": (37.4952, 127.0850),
    "동서울터미널": (37.5344, 127.0941),
}


def make_opener():
    """로그인해서 세션 쿠키를 가진 요청 도구를 만듦"""
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    post_json(opener, "/api/auth/login", {"username": USERNAME, "password": PASSWORD})
    return opener


def post_json(opener, path, body):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(BASE_URL + path, data=data,
                                     headers={"Content-Type": "application/json; charset=utf-8"})
    with opener.open(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def read_questions():
    with open(os.path.join(HERE, "questions.csv"), encoding="utf-8-sig") as file:
        return list(csv.DictReader(file))


def run_questions(opener, questions):
    """질문마다 compare API를 불러 결과를 모음"""
    results = []
    for question in questions:
        lat, lng = PLACES[question["place"]]
        body = {"lat": lat, "lng": lng, "kind": question["kind"],
                "mood": question["mood"], "moodWord": question["moodWord"]}
        answer = post_json(opener, "/api/live/rag/compare", body)
        results.append({"question": question, "answer": answer})
        print(f'{question["id"]:>2}. {question["mood"]} ({question["type"]}) 후보 {answer["candidateCount"]}곳'
              f' (임베딩 {answer["embeddedCount"]}곳) → A {len(answer["keyword"])} · B {len(answer["vector"])}'
              f' · C {len(answer["rag"])}')
    return results


def pool_places(answer):
    """A·B·C 상위 5를 한 바구니에 (겹치면 한 번만, 나온 순서대로)"""
    pooled = []
    seen = set()
    for method in ("keyword", "vector", "rag"):
        for place in answer[method]:
            if place["id"] not in seen:
                seen.add(place["id"])
                pooled.append(place)
    return pooled


def read_reviews(place_ids):
    """정답 표시용 리뷰를 DB에서 (가게마다 긴 순 몇 개). 도커 DB 컨테이너에 psql로 물어봄"""
    if not place_ids:
        return {}
    id_list = ",".join("'" + place_id.replace("'", "''") + "'" for place_id in place_ids)
    sql = ("SELECT place_id, string_agg(left(replace(content, E'\\n', ' '), %d), ' || ' ORDER BY length(content) DESC) "
           "FROM (SELECT place_id, content, ROW_NUMBER() OVER (PARTITION BY place_id ORDER BY length(content) DESC) AS n "
           "FROM retrip_review WHERE place_id IN (%s)) t WHERE n <= %d GROUP BY place_id"
           % (REVIEW_MAX_CHARS, id_list, REVIEWS_PER_PLACE))
    output = subprocess.run(["docker", "exec", DB_CONTAINER, "psql", "-U", "postgres", "-d", "tripbiseo",
                             "-tA", "-F", "\t", "-c", sql], capture_output=True, check=True).stdout.decode("utf-8")
    reviews = {}
    for line in output.splitlines():
        if "\t" in line:
            place_id, text = line.split("\t", 1)
            reviews[place_id] = text
    return reviews


def write_labels(results):
    """정답 표시용 표. 어느 방식이 골랐는지는 안 적음 (블라인드)"""
    rows = []
    all_ids = set()
    for result in results:
        for place in pool_places(result["answer"]):
            rows.append((result["question"], place))
            all_ids.add(place["id"])
    reviews = read_reviews(sorted(all_ids))
    path = os.path.join(HERE, "labels.csv")
    with open(path, "w", encoding="utf-8-sig", newline="") as file:  # utf-8-sig: 엑셀에서 한글이 안 깨지게
        writer = csv.writer(file)
        writer.writerow(["qid", "kind", "mood", "placeId", "name", "reviews", "label"])
        for question, place in rows:
            writer.writerow([question["id"], question["kind"], question["mood"], place["id"], place["name"],
                             reviews.get(place["id"], ""), ""])
    print(f"정답 표시할 가게 {len(rows)}줄 → {path}")


def main():
    opener = make_opener()
    results = run_questions(opener, read_questions())
    with open(os.path.join(HERE, "results.json"), "w", encoding="utf-8") as file:
        json.dump(results, file, ensure_ascii=False, indent=1)
    write_labels(results)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # 윈도우 콘솔에서 한글 출력
    main()
