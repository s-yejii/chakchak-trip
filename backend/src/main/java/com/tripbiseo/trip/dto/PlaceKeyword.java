package com.tripbiseo.trip.dto;

import lombok.Data;

/** 장소 방문자 키워드 한 줄 (place_keyword): "디저트가 맛있어요" 11,372표 (11/19 ①) */
@Data
public class PlaceKeyword {
    private String placeId;
    private String keyword;
    private int cnt;
}
