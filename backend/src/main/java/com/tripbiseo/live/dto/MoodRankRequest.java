package com.tripbiseo.live.dto;

import java.util.List;

import lombok.Data;

/** POST /api/live/rag/rank 요청: 이 가게들을 이 분위기로 줄 세워 봐 (개발·평가용) */
@Data
public class MoodRankRequest {
    private String mood;            // 예: "조용하고 작업하기 좋은"
    private List<String> placeIds;  // 후보 가게 id
}
