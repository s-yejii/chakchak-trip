package com.tripbiseo.member.dto;

import lombok.Data;

/** 공유 코드가 가리키는 여행과 그 주인 (DB trip_share + trip). 서버 안에서만 씀 */
@Data
public class ShareTarget {
    private Long tripId;
    private Long memberId; // 여행 주인
}
