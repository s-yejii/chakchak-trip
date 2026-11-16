package com.tripbiseo.plan.dto;

import lombok.Data;

/** 장소 하나가 든 250m 칸의 평소 붐빔 (② 찬기 crowd_hour 읽기만, 11/16 "조용한" 점수용) */
@Data
public class PlaceCrowd {
    private String placeId;   // 장소 번호 (retrip_place.id)
    private Integer seoulPct; // 여행 요일 낮(10~21시) 평균 서울 순위 0~100 (높을수록 붐빔)
}
