package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.StatusContext;
import com.tripbiseo.trip.dto.Stop;

// 11/20 주형: 스시이세이(13:10 도착, 머묾 1시간)에서 14:09에 "지금 출발해야 해요 · 동서울 16:40"이 떴어요 (예약까지 2시간 넘게 남음)
//   시연 계정만 여유가 30분 넘으면 출발 알림을 안 내요. 일반 계정은 예지가 만든 그대로
class LiveLeaveRoomyTest {

    private final LiveAlertMaker maker = new LiveAlertMaker();

    private StatusContext context(boolean isDemo, String etaOfBus) {
        Stop lunch = new Stop();
        lunch.setId(1L);
        lunch.setName("스시이세이");
        lunch.setCategory("일식");
        Stop bus = new Stop();
        bus.setId(2L);
        bus.setName("동서울종합터미널");
        bus.setTime("16:40");
        bus.setFixed(true);
        StatusContext context = new StatusContext();
        context.setDemo(isDemo);
        context.setCurrent(lunch);
        context.setNextStop(bus);
        context.setNow(Geo.toMinutes("14:10"));
        context.setLeaveAt(Geo.toMinutes("14:10"));
        context.getEtaMap().put(2L, Geo.toMinutes(etaOfBus));
        return context;
    }

    @Test
    void 시연_계정은_예약까지_넉넉하면_출발_알림_없음() {
        assertNull(maker.makeLeave(context(true, "14:32")));
    }

    @Test
    void 시연_계정도_여유가_빠듯하면_출발_알림() {
        assertNotNull(maker.makeLeave(context(true, "16:20")));
    }

    @Test
    void 일반_계정은_그대로_출발_알림() {
        assertNotNull(maker.makeLeave(context(false, "14:32")));
    }
}
