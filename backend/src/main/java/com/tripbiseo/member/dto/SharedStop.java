package com.tripbiseo.member.dto;

import lombok.Data;

/** 공유 일정의 장소 한 칸 (일정 칸에서 보여 줄 것만 옮겨 담음) */
@Data
public class SharedStop {
    private String time;     // 시작 시각 HH:mm
    private String name;
    private String category;
    private Integer stay;    // 머무는 시간(분)
    private Double lat;
    private Double lng;
    private String placeId;  // retrip_place.id (사진·네이버 지도 연결용), 없으면 null
    private String imageUrl; // 장소 대표 사진, 없으면 null (화면이 종류별 기본 그림)
}
