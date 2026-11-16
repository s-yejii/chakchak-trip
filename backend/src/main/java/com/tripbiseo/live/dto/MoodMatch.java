package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 분위기에 맞는 가게 하나 (리뷰 RAG): 점수 + 근거 리뷰 (+ Gemini가 고른 이유·인용) */
@Data
public class MoodMatch {
    private String placeId;
    private double score;                              // 근거 리뷰 거리의 평균 (작을수록 분위기에 맞음)
    private List<String> reviews = new ArrayList<>();  // 근거 리뷰 글 (가까운 순, 2개)
    private String reason;                             // Gemini가 쓴 고른 이유 한 줄 (판단 전이나 실패하면 null)
    private String quote;                              // Gemini가 인용한 리뷰 문장 (reviews 안에 실제로 있는 글만)
}
