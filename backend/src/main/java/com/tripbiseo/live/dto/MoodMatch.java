package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 분위기에 맞는 가게 하나 (리뷰 RAG): 점수 + 근거 리뷰 */
@Data
public class MoodMatch {
    private String placeId;
    private double score;                              // 근거 리뷰 거리의 평균 (작을수록 분위기에 맞음)
    private List<String> reviews = new ArrayList<>();  // 근거 리뷰 글 (가까운 순, 2개)
}
