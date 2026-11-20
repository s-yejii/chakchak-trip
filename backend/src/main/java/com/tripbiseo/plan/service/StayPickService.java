package com.tripbiseo.plan.service;

import java.util.List;

import com.tripbiseo.plan.dto.StayPick;

/** ③ 숙소 추가 화면의 근처 추천 숙소 (11/20) */
public interface StayPickService {

    /** 여행 지역 가운데 근처 숙소 몇 곳 (시연 계정은 시나리오 숙소가 맨 위) */
    List<StayPick> getStayList(long memberId, double lat, double lng);
}
