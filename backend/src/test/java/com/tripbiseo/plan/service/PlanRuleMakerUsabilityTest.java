package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.trip.dto.Place;

// 11/18 사용성 사전 점검에서 나온 일정 규칙 (일반 계정 = 실제 여행: 입력한 날짜·시각 그대로, 시연 계정은 DemoScenario라 여기를 안 지나감)
class PlanRuleMakerUsabilityTest {

    // 칸 틀 계산은 DB·다른 서비스를 안 써서 null로 만들어도 돼요
    private final PlanRuleMaker ruleMaker = new PlanRuleMaker(null, null);

    private PlanConditions timed(String start, String end, String... kinds) {
        PlanConditions conditions = new PlanConditions();
        conditions.setStartDate("2026-11-19");
        conditions.setEndDate("2026-11-19");
        conditions.setStartTime(start);
        conditions.setEndTime(end);
        if (kinds.length > 0) {
            conditions.setKinds(new ArrayList<>(List.of(kinds)));
        }
        return conditions;
    }

    private Place place(String id, String name, String kind, String category) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setKind(kind);
        place.setCategory(category);
        return place;
    }

    @Test
    void 저녁_시간에_시작하면_첫_칸은_저녁() {
        List<String[]> slots = ruleMaker.getSlotList(timed("19:30", "22:30"));
        assertEquals("food", slots.get(0)[0]); // 예전엔 19:30 서울숲 70분 → 밥 없음
        assertEquals("19:30", slots.get(0)[1]);
    }

    @Test
    void 술만_고르면_저녁_칸에_술집도_들어가고_2차는_한_번() {
        assertEquals("bar,food", ruleMaker.dinnerKindsOf(timed("18:00", "23:30", "bar")));
        assertEquals("food", ruleMaker.dinnerKindsOf(timed("11:00", "22:00", "food", "cafe", "bar")));
        List<String> kinds = List.of("bar", "food", "cafe", "tour");
        assertEquals("bar", ruleMaker.outingKindsOf(kinds, 20 * 60, "bar,food", "")); // 저녁 뒤 2차
        assertFalse(ruleMaker.outingKindsOf(kinds, 21 * 60 + 30, "bar", "bar,food").contains("bar")); // 3차는 없음
    }

    @Test
    void 볼거리_두_곳_다음엔_카페_먼저() {
        List<String> kinds = List.of("food", "cafe", "tour");
        assertTrue(ruleMaker.outingKindsOf(kinds, 16 * 60, "tour", "tour,culture").startsWith("cafe"));
        assertTrue(ruleMaker.outingKindsOf(kinds, 15 * 60, "tour", "cafe").startsWith("tour"));
    }

    @Test
    void 저녁_뒤_2차는_안주가_밥인_술집_말고() {
        Map<String, Place> candidates = new LinkedHashMap<>();
        candidates.put("1", place("1", "미토", "bar", "이자카야"));
        candidates.put("2", place("2", "코너숍", "bar", "요리주점"));
        candidates.put("3", place("3", "혼술바", "bar", "바(BAR)"));
        Set<String> blocked = new HashSet<>();
        ruleMaker.blockMealBars(blocked, candidates);
        assertEquals(Set.of("1", "2"), blocked);
    }

    @Test
    void 안주가_밥인_술집만_있으면_막지_않음() {
        Map<String, Place> candidates = new LinkedHashMap<>();
        candidates.put("1", place("1", "미토", "bar", "이자카야"));
        Set<String> blocked = new HashSet<>();
        ruleMaker.blockMealBars(blocked, candidates);
        assertTrue(blocked.isEmpty());
    }

    @Test
    void 밤에는_바깥_볼거리_대신_야경_명소만() {
        Map<String, Place> candidates = new LinkedHashMap<>();
        candidates.put("1", place("1", "후암동108계단", "tour", "관광지"));
        candidates.put("2", place("2", "남산서울타워", "tour", "관광지"));
        candidates.put("3", place("3", "트랍", "cafe", "카페"));
        Set<String> night = new HashSet<>();
        ruleMaker.blockNightOutdoor(night, candidates, 21 * 60 + 40);
        assertEquals(Set.of("1"), night);
        Set<String> evening = new HashSet<>();
        ruleMaker.blockNightOutdoor(evening, candidates, 19 * 60);
        assertTrue(evening.isEmpty());
    }

    @Test
    void 고른_시작보다_이른_예약이_있으면_예약부터() {
        PlanConditions conditions = timed("11:00", "20:00");
        FixedVisit fixed = new FixedVisit();
        fixed.setName("어니언 성수");
        fixed.setTime("09:00");
        fixed.setDay(1);
        conditions.setFixed(new ArrayList<>(List.of(fixed)));
        assertEquals(9 * 60, ruleMaker.dayStartMinOf(conditions, 1));
        assertEquals(11 * 60, ruleMaker.dayStartMinOf(timed("11:00", "20:00"), 1));
    }

    @Test
    void 하루_칸에_볼거리가_세_번_연달아_안_나옴() {
        List<String[]> slots = ruleMaker.getSlotList(timed("14:00", "20:00", "tour", "culture"));
        for (int i = 2; i < slots.size(); i++) {
            boolean isSightRun = isSight(slots.get(i)[0]) && isSight(slots.get(i - 1)[0]) && isSight(slots.get(i - 2)[0]);
            assertFalse(isSightRun, slots.get(i)[1] + " 칸까지 볼거리 3연속");
        }
    }

    private boolean isSight(String slotKinds) {
        String first = slotKinds.split(",")[0];
        return first.equals("tour") || first.equals("culture") || first.equals("shop");
    }
}
