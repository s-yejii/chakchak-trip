"""
리뷰 RAG 평가 2단계: 정답 표시(labels.csv)로 A(글자)·B(벡터)·C(RAG) 점수를 계산해요. (④ 신예지, 11/16)

순서: run_eval.py 실행 → labels.csv의 label 칸에 1(맞음)/0(아님) 채우기 → 이 파일 실행
실행 (프로젝트 폴더에서):  python docs/rag-eval/score_eval.py
결과: 화면에 표 + docs/rag-eval/summary.md

지표 (질문마다 계산해서 평균)
  Precision@5  상위 5칸 중 맞은 수 ÷ 5. 덜 골라서 빈 칸도 틀린 것으로 셈 (못 찾는 것도 손해)
  고른 것 중   고른 곳 중 맞은 비율 (적게 골라도 다 맞으면 1.0. 아무것도 안 고른 질문은 빼고 평균)
  Hit@5        상위 5 안에 맞는 곳이 하나라도 있으면 1
  찾은 비율    풀(A·B·C가 고른 곳 전체)에서 정답인 곳 중 이 방식이 찾은 비율 (풀링이라 진짜 재현율은 아님)
  0건 비율     하나도 못 고른 질문 비율
  평균 시간    방식마다 걸린 시간(ms)
"""
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOP = 5
METHODS = [("keyword", "A 글자"), ("vector", "B 벡터"), ("rag", "C RAG")]


def read_labels():
    """(질문 번호, 가게 id) → 1/0. 빈 칸은 빼고 몇 개인지 알려 줌"""
    labels = {}
    empty = 0
    with open(os.path.join(HERE, "labels.csv"), encoding="utf-8-sig") as file:
        for row in csv.DictReader(file):
            value = row["label"].strip()
            if value in ("0", "1"):
                labels[(row["qid"], row["placeId"])] = int(value)
            else:
                empty += 1
    if empty:
        print(f"주의: 정답 칸이 빈 줄 {empty}개 (빈 칸은 틀린 것으로 셈)")
    return labels


def score_question(result, labels):
    """질문 하나: 방식마다 점수"""
    qid = result["question"]["id"]
    answer = result["answer"]
    relevant_in_pool = set()
    for method, _ in METHODS:
        for place in answer[method]:
            if labels.get((qid, place["id"])) == 1:
                relevant_in_pool.add(place["id"])
    scores = {}
    for method, _ in METHODS:
        picked = [place["id"] for place in answer[method][:TOP]]
        hits = [place_id for place_id in picked if labels.get((qid, place_id)) == 1]
        scores[method] = {
            "precision": len(hits) / TOP,
            "accuracy": len(hits) / len(picked) if picked else None,
            "hit": 1 if hits else 0,
            "found": len(hits) / len(relevant_in_pool) if relevant_in_pool else None,
            "empty": 1 if not picked else 0,
            "ms": answer[method + "Ms"],
        }
    return scores


def average(values):
    values = [value for value in values if value is not None]
    return sum(values) / len(values) if values else 0


def make_table(rows, title):
    """rows: 질문마다 점수 묶음 → 마크다운 표"""
    lines = [f"### {title} (질문 {len(rows)}개)", "",
             "| 방식 | Precision@5 | 고른 것 중 | Hit@5 | 찾은 비율 | 0건 비율 | 평균 시간 |", "|---|---|---|---|---|---|---|"]
    for method, name in METHODS:
        scores = [row[method] for row in rows]
        lines.append(f"| {name} | {average([s['precision'] for s in scores]):.2f}"
                     f" | {average([s['accuracy'] for s in scores]):.2f}"
                     f" | {average([s['hit'] for s in scores]):.2f}"
                     f" | {average([s['found'] for s in scores]):.2f}"
                     f" | {average([s['empty'] for s in scores]):.0%}"
                     f" | {average([s['ms'] for s in scores]):.0f}ms |")
    return "\n".join(lines) + "\n"


def make_win_table(results, other, other_name):
    """질문마다 C(RAG)와 다른 방식의 Precision@5를 비교해 이김·비김·짐을 셈 + 진 질문 목록 (왜 졌는지 볼 것)"""
    counts = {"same": [0, 0, 0], "diff": [0, 0, 0]}  # [이김, 비김, 짐]
    losses = []
    for result in results:
        question = result["question"]
        rag = result["scores"]["rag"]["precision"]
        base = result["scores"][other]["precision"]
        if rag > base:
            counts[question["type"]][0] += 1
        elif rag == base:
            counts[question["type"]][1] += 1
        else:
            counts[question["type"]][2] += 1
            losses.append(f'{question["id"]}. {question["mood"]} ({question["type"]}, C {rag:.1f} < {base:.1f})')
    lines = [f"### 질문별 승패: C RAG vs {other_name} (Precision@5 기준)", "",
             "| 질문 | 이김 | 비김 | 짐 |", "|---|---|---|---|"]
    for kind in ("same", "diff"):
        win, tie, lose = counts[kind]
        lines.append(f"| {kind} | {win} | {tie} | {lose} |")
    total = [counts["same"][i] + counts["diff"][i] for i in range(3)]
    lines.append(f"| 전체 | {total[0]} | {total[1]} | {total[2]} |")
    if losses:
        lines.append("")
        lines.append("C가 진 질문 (리뷰를 열어 왜 졌는지 확인):")
        for loss in losses:
            lines.append(f"- {loss}")
    return "\n".join(lines) + "\n"


def main():
    with open(os.path.join(HERE, "results.json"), encoding="utf-8") as file:
        results = json.load(file)
    labels = read_labels()

    no_embedding = [r["question"]["id"] for r in results if r["answer"]["embeddedCount"] == 0]
    if no_embedding:
        print(f"주의: 임베딩이 없어 C가 예전 방식으로 돈 질문 {no_embedding} (임베딩을 채우고 다시 돌리세요)")

    rows_all, rows_same, rows_diff = [], [], []
    for result in results:
        scores = score_question(result, labels)
        result["scores"] = scores  # 승패 세기에 씀
        rows_all.append(scores)
        if result["question"]["type"] == "same":
            rows_same.append(scores)
        else:
            rows_diff.append(scores)

    report = "## 리뷰 RAG 평가 결과\n\n"
    report += make_table(rows_all, "전체") + "\n"
    report += make_table(rows_same, "same: 말에 찾을 글자가 그대로 있음") + "\n"
    report += make_table(rows_diff, "diff: 말과 리뷰 표현이 다름") + "\n"
    report += make_win_table(results, "keyword", "A 글자") + "\n"
    report += make_win_table(results, "vector", "B 벡터") + "\n"
    print(report)
    with open(os.path.join(HERE, "summary.md"), "w", encoding="utf-8") as file:
        file.write(report)
    print("→ docs/rag-eval/summary.md 저장")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # 윈도우 콘솔에서 한글 출력
    main()
