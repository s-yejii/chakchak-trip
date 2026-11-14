package com.tripbiseo.live.dto;

import lombok.Data;

/** 분위기 질문과 가까운 리뷰 한 줄 (리뷰 RAG, DB → 서버). 가게마다 가까운 순 2개까지 */
@Data
public class MoodReview {
    private String placeId;
    private String reviewId;
    private String content;
    private double distance; // 질문과 거리 (코사인 거리 <=>, 0에 가까울수록 뜻이 비슷)
}
