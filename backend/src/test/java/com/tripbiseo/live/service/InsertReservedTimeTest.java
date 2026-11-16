package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.trip.dto.Stop;

// 여행 중 "비슷한 가게 담기"에 예약 시각을 같이 보내면 고정 일정으로 (11/16 ③, ④ 예지 OK)
// 예: 스시도에 못 가게 돼서 스시이세이를 전화로 15:00에 예약 → 🔒 15:00 칸
class InsertReservedTimeTest {

    private Stop newStop() {
        Stop stop = new Stop(); // 담기로 만든 새 칸 (toNewStop과 같은 처음 상태)
        stop.setName("스시이세이 롯데백화점 에비뉴엘 잠실점");
        stop.setFixed(false);
        return stop;
    }

    @Test
    void 예약_시각이_있으면_고정_일정_그_시각() {
        Stop stop = newStop();
        LiveServiceImpl.setReservedTime(stop, "15:00");
        assertTrue(stop.getFixed());
        assertEquals("15:00", stop.getTime());
    }

    @Test
    void 예약_시각이_없으면_지금처럼_고정_아님() {
        Stop stop = newStop();
        LiveServiceImpl.setReservedTime(stop, null);
        assertFalse(stop.getFixed());
        assertNull(stop.getTime());
        LiveServiceImpl.setReservedTime(stop, " ");
        assertFalse(stop.getFixed());
    }

    @Test
    void 시각_모양이_틀리면_400() {
        for (String wrong : new String[] {"3시", "25:00", "15:60", "1500"}) {
            ApiException e = assertThrows(ApiException.class, () -> LiveServiceImpl.setReservedTime(newStop(), wrong));
            assertEquals(400, e.getStatus());
        }
    }
}
