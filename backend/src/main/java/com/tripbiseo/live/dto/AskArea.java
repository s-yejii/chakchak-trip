package com.tripbiseo.live.dto;

import lombok.Data;

/** AI에게 묻기에서 내 위치 대신 기준으로 삼은 곳 (예: "강남역 근처 카페"의 강남역) */
@Data
public class AskArea {
    private String name; // 질문에 나온 이름 그대로 (예: "강남역")
    private double lat;  // 카카오 검색으로 찾은 그곳 좌표
    private double lng;
}
