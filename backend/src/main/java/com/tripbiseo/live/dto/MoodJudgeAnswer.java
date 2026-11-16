package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Gemini 답 (리뷰 RAG의 G): 분위기에 맞는 가게 번호 + 이유 + 리뷰에서 그대로 옮긴 문장 */
@Data
public class MoodJudgeAnswer {
    private List<Pick> picks = new ArrayList<>();

    @Data
    public static class Pick {
        private Integer number;  // 프롬프트에 적은 가게 번호 (1부터)
        private String reason;   // 고른 이유 한 줄
        private String quote;    // 근거 리뷰에서 그대로 옮긴 짧은 문장
    }
}
