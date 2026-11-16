package com.tripbiseo.member.dto;

import lombok.Data;

/** 장소 대표 사진 (retrip_place.image_url 읽기). 공유 화면은 로그인 없이 열려서 사진도 공유 응답에 같이 담아요 */
@Data
public class PlaceImage {
    private String placeId;
    private String imageUrl;
}
