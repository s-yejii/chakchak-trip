package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Stop;

// 숙소 칸이 하루 끝 시각을 넘지 않게 (11/17): 마지막 칸이 예약이 아니면 머무는 시간을 줄임
class PlanRuleMakerStayEndTest {

    private final PlanRuleMaker ruleMaker = new PlanRuleMaker(null, null); // DB 안 씀
    private static final double[] STAY = {37.5616, 126.9827};            // 명동 숙소
    private static final int END_20_00 = 20 * 60;

    private Stop stopOf(String time, int stay, boolean isFixed, double lat, double lng) {
        Stop stop = new Stop();
        stop.setDay(1);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setLat(lat);
        stop.setLng(lng);
        return stop;
    }

    @Test
    void 마지막_칸이_예약이_아니면_줄여서_숙소가_끝_시각_안에() {
        Stop last = stopOf("18:30", 90, false, 37.5512, 126.9882); // 남산 → 20:00 끝 + 이동이면 넘침
        List<Stop> stops = new ArrayList<>(List.of(stopOf("15:00", 60, false, 37.57, 126.99), last));
        ruleMaker.trimLastStopForStay(stops, 1, STAY, END_20_00);
        int move = PlanTravelTime.minutesBetween(new double[] {last.getLat(), last.getLng()}, STAY);
        assertEquals(true, 18 * 60 + 30 + last.getStay() + move <= END_20_00 + 10); // 10분 단위 올림 안
        assertEquals(true, last.getStay() < 90);
    }

    @Test
    void 마지막_칸이_예약이면_그대로() {
        Stop dinner = stopOf("19:00", 120, true, 37.5658, 126.9906); // 회식 21:00 끝
        ruleMaker.trimLastStopForStay(new ArrayList<>(List.of(dinner)), 1, STAY, END_20_00);
        assertEquals(120, dinner.getStay());
    }

    @Test
    void 줄여도_40분보다_짧게는_안_함() {
        Stop last = stopOf("19:40", 60, false, 37.5512, 126.9882);
        ruleMaker.trimLastStopForStay(new ArrayList<>(List.of(last)), 1, STAY, END_20_00);
        assertEquals(40, last.getStay());
    }

    @Test
    void 넘지_않으면_그대로() {
        Stop last = stopOf("17:00", 60, false, 37.5612, 126.9830); // 숙소 바로 옆, 18:00 끝
        ruleMaker.trimLastStopForStay(new ArrayList<>(List.of(last)), 1, STAY, END_20_00);
        assertEquals(60, last.getStay());
    }
}
