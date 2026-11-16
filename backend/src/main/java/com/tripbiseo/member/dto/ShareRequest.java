package com.tripbiseo.member.dto;

import lombok.Data;

/** 공유 링크 만들기 요청 (POST /api/share) */
@Data
public class ShareRequest {
    private Long tripId; // 공유할 내 여행 번호
}
