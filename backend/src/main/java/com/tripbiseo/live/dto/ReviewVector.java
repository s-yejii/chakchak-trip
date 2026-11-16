package com.tripbiseo.live.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 임베딩으로 바꾼 리뷰 하나 (리뷰 RAG, 서버 → DB) */
@Data
@AllArgsConstructor
public class ReviewVector {
    private String reviewId;
    private String embedding; // "[0.012,-0.08,…]" 글자. SQL에서 ::vector로 바꿔 저장
}
