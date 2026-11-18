package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Stop;

// 지금 칸 고르기: 좌표가 같은 칸이 둘이면 앞 칸 (11/17: 1일차 낮 숙소 15:00 · 밤 숙소 21:30이 같은 좌표)
class CurrentStopTieTest {

    private final LiveStopFinder finder = new LiveStopFinder(); // 계산만 하는 도우미

    private Stop hotelAt(long id, String time) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setTime(time);
        stop.setState("todo");
        stop.setLat(37.5616);
        stop.setLng(126.9827);
        return stop;
    }

    @Test
    void 같은_좌표면_먼저_갈_낮_숙소() {
        Stop day = hotelAt(1L, "15:00");
        Stop night = hotelAt(2L, "21:30");
        assertEquals(day, finder.findCurrentStop(List.of(day, night), 37.5617, 126.9828)); // 숙소 근처 10m
    }

    @Test
    void 더_가까운_칸이_있으면_그_칸() {
        Stop far = hotelAt(1L, "15:00");
        far.setLat(37.5626); // 약 110m 북쪽
        Stop near = hotelAt(2L, "21:30");
        assertEquals(near, finder.findCurrentStop(List.of(far, near), 37.5616, 126.9827));
    }
}
