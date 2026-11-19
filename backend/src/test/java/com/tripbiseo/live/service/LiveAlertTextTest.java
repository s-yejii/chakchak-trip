package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.StatusContext;
import com.tripbiseo.trip.dto.Stop;

// 11/17 주형: 놓침 카드 문구는 택시 도착·요금(TMAP 실제 값)으로 쉽게, 이동 중엔 출발 알림 안 냄
class LiveAlertTextTest {

    private final LiveAlertMaker maker = new LiveAlertMaker(); // 문구만 만드는 도우미

    private Stop sushi() {
        Stop stop = new Stop();
        stop.setName("스시도");
        stop.setTime("12:30");
        return stop;
    }

    // 11/19 PPT 14장: 택시로도 늦으면 시각·요금 대신 한마디로 (늦는 시각은 카드 첫 줄 "13:05 도착 · 35분 늦음")
    @Test
    void 택시로도_늦으면_한마디로() {
        StatusContext context = new StatusContext();
        context.setFastestSlack(-15);
        context.setTaxiArrive(Geo.toMinutes("12:45"));
        context.setTaxiFare(20300);
        assertEquals("택시로 가도 늦어요", maker.makeLostText(context, sushi()));
    }

    @Test
    void 택시_길을_못_받으면_예전_문구() {
        StatusContext context = new StatusContext();
        context.setFastestSlack(-15);
        assertEquals("가장 빠른 방법으로 가도 약속 시각(12:30)보다 15분 늦어요", maker.makeLostText(context, sushi()));
    }

    // 11/19 주형: 예약 49m 앞 카페 (1분 거리, 계획은 18:54에 나가 18:55 도착) → 18:09엔 "늦어요" 없음, 18:54엔 있음
    @Test
    void 바로_앞_카페면_나갈_때가_돼야_지금_나가라고() {
        Stop dinner = sushi();
        dinner.setId(7L);
        dinner.setTime("19:00");
        StatusContext context = new StatusContext();
        context.setFixedStop(dinner);
        context.setLeaveAt(Geo.toMinutes("18:54"));
        context.getEtaMap().put(7L, Geo.toMinutes("18:55"));
        context.setSlack(5);
        context.setNow(Geo.toMinutes("18:09"));
        assertFalse(maker.isMustLeaveNow(context));
        context.setNow(Geo.toMinutes("18:54"));
        assertTrue(maker.isMustLeaveNow(context));
    }

    // 11/19 주형: 예약 앞 카페에선 출발 알림을 5분 전 한 번만
    @Test
    void 카페에선_출발_알림이_5분_전_한_번만() {
        Stop cafe = new Stop();
        cafe.setName("공간갑");
        cafe.setCategory("카페,디저트");
        Stop dinner = sushi();
        dinner.setId(7L);
        dinner.setFixed(true);
        StatusContext context = new StatusContext();
        context.setCurrent(cafe);
        context.setNextStop(dinner);
        context.setLeaveAt(Geo.toMinutes("18:54"));
        context.setNow(Geo.toMinutes("18:45")); // 9분 전
        assertNull(maker.makeLeave(context));
        context.setNow(Geo.toMinutes("18:50")); // 4분 전
        assertNotNull(maker.makeLeave(context));
        context.setNow(Geo.toMinutes("18:54")); // 지금
        assertNull(maker.makeLeave(context));
    }

    @Test
    void 앞_칸을_떠났으면_이동_중() {
        Stop cafe = new Stop();
        cafe.setDepartedAt("2026-11-16T18:45:06");
        assertTrue(LiveAlertMaker.hasLeftAStop(List.of(cafe, sushi())));
        assertFalse(LiveAlertMaker.hasLeftAStop(List.of(sushi())));
    }
}
