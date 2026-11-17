package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Stop;

// 여행 중 다음 예약 앞 카페 머무는 시간 (11/17 주형 결정, 예지 OK): 예약 5분 전 도착에 맞춰 15~60분, 다음 예약 없으면 30분
class CafeStayTest {

    @Test
    void 시연_카드_30분이면_여유_13분일_때_38분_쉬고_5분_전_도착() {
        assertEquals(38, CafeStay.stayMinutes(13)); // 18:05 내려와 원더브레드, 30분 있으면 회식 13분 전 → 8분 더
    }

    @Test
    void 시연_콘웨이커피_30분이면_1분_늦을_때_24분으로_줄여_5분_전_도착() {
        assertEquals(24, CafeStay.stayMinutes(-1)); // 미리보기 "1분 늦어요" 대신 24분 쉬었다 가기
    }

    @Test
    void 여유가_2시간이면_최대_60분() {
        assertEquals(60, CafeStay.stayMinutes(84));
    }

    @Test
    void 다음_예약이_없으면_30분() {
        assertEquals(30, CafeStay.stayMinutes(null));
    }

    @Test
    void 십오분도_못_있으면_권하지_않음() {
        assertEquals(15, CafeStay.stayMinutes(-10));
        assertNull(CafeStay.stayMinutes(-11)); // 화면 "들르면 빠듯해요"
    }

    @Test
    void 넣는_카페_칸을_예약에_맞춤() {
        Stop cafe = new Stop(); // 새로 넣는 칸 (id 없음)
        cafe.setCategory("카페");
        cafe.setStay(30);
        Stop dinner = new Stop();
        dinner.setId(7L);
        dinner.setFixed(true);
        List<Stop> plan = new ArrayList<>(List.of(cafe, dinner));
        assertTrue(CafeStay.fitNewCafe(plan, dinner, -1)); // 30분이면 1분 늦음
        assertEquals(24, cafe.getStay());
        assertFalse(CafeStay.fitNewCafe(plan, dinner, 5)); // 다시 계산해 5분 전이면 그대로
    }

    @Test
    void 카페_종류_판별() {
        assertTrue(CafeStay.isCafe("베이커리"));
        assertTrue(CafeStay.isCafe("카페,디저트"));
        assertFalse(CafeStay.isCafe("육류,고기요리"));
    }
}
