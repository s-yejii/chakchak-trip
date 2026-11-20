package com.tripbiseo.plan.dto;

import lombok.Data;

/** ③ 숙소 추가 화면의 근처 추천 숙소 한 곳 (11/20). 관광공사(TourAPI)에 등록된 숙박만 */
@Data
public class StayPick {
    private String placeId;
    private String name;
    private String kind;      // 호텔 · 게스트하우스 · 한옥 게스트하우스 (관광공사 분류)
    private String address;
    private Double lat;
    private Double lng;
    private String imageUrl;  // 관광공사 사진, 없으면 null
    private Integer distance; // 여행 지역 가운데에서 거리(m)
}
