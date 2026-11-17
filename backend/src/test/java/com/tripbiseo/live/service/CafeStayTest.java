package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// 여행 중 카페 머무는 시간 (11/17 주형 결정, 예지 OK): 기본 30분, 여유가 많으면 예약 15분 전 도착까지 최대 60분
class CafeStayTest {

    // 30분 머문 뒤 남는 여유 → 머무는 분
    private int stayOf(Integer slackAfterBase) {
        return CafeStay.BASE_MIN + CafeStay.extraMinutes(slackAfterBase);
    }

    @Test
    void 시연_여유_43분이면_30분_머물고_여유_13분() {
        int slackAfterBase = 43 - 0 - 30; // 순환버스로 내려와 여유 43분, 가는 길 +0분
        assertEquals(30, stayOf(slackAfterBase));
        assertEquals(13, slackAfterBase - CafeStay.extraMinutes(slackAfterBase));
    }

    @Test
    void 여유_2시간이면_60분_머물고_나머지는_여유() {
        int slackAfterBase = 120 - 6 - 30;
        assertEquals(60, stayOf(slackAfterBase));
        assertEquals(54, slackAfterBase - CafeStay.extraMinutes(slackAfterBase));
    }

    @Test
    void 조금_남으면_예약_15분_전까지만_늘림() {
        assertEquals(40, stayOf(25)); // 30분 뒤 여유 25분 → 10분 더 → 여유 15분
    }

    @Test
    void 다음_예약이_없으면_30분() {
        assertEquals(30, stayOf(null));
    }

    @Test
    void 여유가_부족하면_30분_그대로_카드는_늦어요() {
        int slackAfterBase = 20 - 0 - 30; // -10 → 카드 "들르면 늦어요"
        assertEquals(30, stayOf(slackAfterBase));
        assertTrue(slackAfterBase - CafeStay.extraMinutes(slackAfterBase) < 0);
    }

    @Test
    void 카페_종류_판별() {
        assertTrue(CafeStay.isCafe("베이커리"));
        assertTrue(CafeStay.isCafe("카페,디저트"));
        assertFalse(CafeStay.isCafe("육류,고기요리"));
        assertFalse(CafeStay.isCafe(null));
    }
}
