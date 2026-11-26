package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/recommend 응답 */
@Data
public class RecommendResponse {
    private List<RecommendPlace> places = new ArrayList<>(); // 최대 3개(가는 길 모드는 5개), 점수(보정 평점 + 원하는 태그) 순
    private int closedSkipped;   // 도착 때 닫힌 곳이라 뺀 수 (평점 없는 곳은 후보에서 미리 빠져서 안 셈)
}
