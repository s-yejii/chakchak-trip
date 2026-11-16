package com.tripbiseo.plan.dto;

import lombok.Data;

/** 한강버스 선착장 하나 (retrip_place의 source='hangang' 줄, 읽기만) */
@Data
public class HangangBusPier {
    private String id;     // hangang-oksu
    private String name;   // 한강버스 옥수선착장
    private double lat;
    private double lng;
    private int meters;    // 기준 좌표에서 직선 거리(m)
}
