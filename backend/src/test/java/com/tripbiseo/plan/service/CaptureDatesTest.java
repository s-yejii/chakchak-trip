package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.AiReceipt;

// 캡처 날짜의 연도는 서버가 정함 + 영수증 합계 비교는 할인까지 빼고 (11/16 OCR 점검)
class CaptureDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    // 합계 비교는 DB·Gemini를 안 써서 null로 만들어도 돼요
    private final CaptureServiceImpl captureService = new CaptureServiceImpl(null, null, null, null);

    @Test
    void 영수증_어제_날짜는_올해() {
        assertEquals("2026-11-15", CaptureDates.latestUntilToday("2025-11-15", TODAY)); // 예전엔 AI가 2025로 읽었어요
    }

    @Test
    void 영수증_오늘보다_뒤_날짜면_작년() {
        assertEquals("2025-12-24", CaptureDates.latestUntilToday("2026-12-24", TODAY));
    }

    @Test
    void 예약_다음_주는_올해_지난_날짜는_내년() {
        assertEquals("2026-11-21", CaptureDates.nextFromToday("2027-11-21", TODAY));
        assertEquals("2027-11-14", CaptureDates.nextFromToday("2026-11-14", TODAY));
        assertEquals("2026-11-16", CaptureDates.nextFromToday("2026-11-16", TODAY)); // 오늘은 오늘
    }

    @Test
    void 숙소_해를_넘기는_체크아웃() {
        assertEquals("2027-01-02", CaptureDates.afterCheckIn("2026-01-02", "2026-12-31"));
        assertEquals("2026-11-17", CaptureDates.afterCheckIn("2026-11-17", "2026-11-16"));
    }

    @Test
    void 이상한_날짜_글자는_그대로() {
        assertEquals("모름", CaptureDates.nextFromToday("모름", TODAY));
    }

    @Test
    void 할인_있는_영수증은_합계와_같으면_확인_필요_아님() {
        AiReceipt receipt = new AiReceipt();
        receipt.setItemsTotal("171,000");
        receipt.setDiscountTotal("21,000");
        assertFalse(captureService.isSumDifferent(receipt, 150000)); // 산청숯불가든: 171,000 - 21,000 = 150,000
    }

    @Test
    void 품목_합이_합계와_다르면_확인_필요() {
        AiReceipt receipt = new AiReceipt();
        receipt.setItemsTotal("50,000");
        assertTrue(captureService.isSumDifferent(receipt, 45000));
    }

    @Test
    void 품목_합을_못_읽으면_다르다고_안_함() {
        assertFalse(captureService.isSumDifferent(new AiReceipt(), 45000));
    }
}
