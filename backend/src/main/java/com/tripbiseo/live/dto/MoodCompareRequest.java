package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /api/live/rag/compare 요청 (평가용): 이 점 500m 안 이 종류 가게를 세 방식으로 골라 봐 */
@Data
public class MoodCompareRequest {
    private double lat;
    private double lng;
    private String kind;      // cafe / food / bar
    private String mood;      // 예: "오래 앉아 있어도 눈치 안 보이는" (B·C가 씀)
    private String moodWord;  // 예: "오래" (A가 씀)
}
