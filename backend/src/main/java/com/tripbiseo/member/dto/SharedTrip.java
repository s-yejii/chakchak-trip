package com.tripbiseo.member.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 로그인 없이 보는 공유 일정 (GET /api/share/{code} 응답). 메모·도착 기록·출발지·회원 정보는 넣지 않음 */
@Data
public class SharedTrip {
    private String title;
    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd
    private List<SharedDay> days = new ArrayList<>();
}
