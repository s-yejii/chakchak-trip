package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.HangangBusOption;

/** ③ 한강버스 추천 (11/16): 예약까지 시간이 많이 뜰 때 "한강버스로도 가요" 한 줄 */
public interface HangangBusService {

    /**
     * 지금 위치 → 목적지(예약 arriveBy "HH:mm")를 한강버스로 가는 방법 하나. 권할 게 없으면 null.
     * now("HH:mm")가 null이면 지금 시각(시연 계정은 시연 시계). 비 예보는 오늘만 봐요
     */
    HangangBusOption getOption(double fromLat, double fromLng, double toLat, double toLng, String arriveBy, String now);
}
