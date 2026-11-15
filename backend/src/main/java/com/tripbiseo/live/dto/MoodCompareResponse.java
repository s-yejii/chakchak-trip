package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /api/live/rag/compare 응답 (평가용): 같은 후보에서 A·B·C가 고른 가게(상위 5)와 걸린 시간 */
@Data
public class MoodCompareResponse {
    private int candidateCount;                                // 후보 가게 수 (500m 안 그 종류)
    private int embeddedCount;                                 // 후보 중 임베딩된 리뷰가 있는 가게 수 (0이면 C도 예전 방식으로 돎)
    private List<MoodComparePlace> keyword = new ArrayList<>(); // A: 리뷰 2개 이상에 moodWord 글자 (예전 방식)
    private List<MoodComparePlace> vector = new ArrayList<>();  // B: 임베딩 거리 순 (벡터만)
    private List<MoodComparePlace> rag = new ArrayList<>();     // C: 벡터 → Gemini 판단 → 인용 검사 (앱에 들어간 방식)
    private long keywordMs;
    private long vectorMs;
    private long ragMs;
}
