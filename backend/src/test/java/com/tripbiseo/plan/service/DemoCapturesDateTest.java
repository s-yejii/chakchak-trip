package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.CaptureResponse;

// 11/20: 시연 캡처 날짜는 촬영일(11/16~17)이 아니라 지금 만드는 여행 1일차·2일차로
class DemoCapturesDateTest {

    private final DemoCaptures captures = new DemoCaptures();

    private CaptureResponse reserve(String date) {
        CaptureResponse response = new CaptureResponse();
        response.setName("스시도");
        response.setDate(date);
        response.setTime("12:30");
        return response;
    }

    @Test
    void 촬영_1일차_예약은_여행_1일차로() {
        assertEquals("2026-12-03", captures.shiftToTrip(reserve("2026-11-16"), "2026-12-03").getDate());
    }

    @Test
    void 촬영_2일차_예약은_여행_2일차로() {
        CaptureResponse moved = captures.shiftToTrip(reserve("2026-11-17"), "2026-11-20");
        assertEquals("2026-11-21", moved.getDate());
        assertEquals("12:30", moved.getTime());
    }
}
