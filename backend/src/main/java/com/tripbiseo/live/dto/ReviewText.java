package com.tripbiseo.live.dto;

import lombok.Data;

/** 아직 임베딩이 없는 리뷰 하나 (리뷰 RAG, DB → 서버) */
@Data
public class ReviewText {
    private String reviewId;
    private String content;
}
