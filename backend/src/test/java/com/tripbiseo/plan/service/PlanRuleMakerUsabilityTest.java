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

import com.tripbiseo.plan.dto.AiDraftStop;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.trip.dto.Stop;
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
        assertEquals("bar", ruleMaker.outingKindsOf(kinds, 20 * 60, "food", "", false)); // 저녁 뒤 2차
        assertFalse(ruleMaker.outingKindsOf(kinds, 21 * 60 + 30, "bar", "bar,food", false).contains("bar")); // 끝 장소가 없으면 3차 없음 (2차는 끝 시간 없음)
        assertEquals("bar", ruleMaker.outingKindsOf(kinds, 21 * 60 + 30, "bar", "food", true)); // 끝 장소를 정해 시간이 남으면 3차
    }

    @Test
    void 볼거리_두_곳_다음엔_카페_먼저() {
        List<String> kinds = List.of("food", "cafe", "tour");
        assertTrue(ruleMaker.outingKindsOf(kinds, 16 * 60, "tour", "tour,culture", false).startsWith("cafe"));
        assertTrue(ruleMaker.outingKindsOf(kinds, 15 * 60, "tour", "cafe", false).startsWith("tour"));
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

    @Test
    void 일정_사이가_1시간30분_넘게_비면_알림() {
        List<AiDraftStop> stops = new ArrayList<>(List.of(draft(2, "14:00", 70), draft(2, "18:10", 70), draft(1, "11:00", 70), draft(1, "12:30", 70)));
        List<String> warnings = new ArrayList<>();
        ruleMaker.addLongGapWarnings(stops, warnings);
        assertEquals(List.of("2일차 15:10~18:10이 비어 있어요 · 근처에 넣을 곳이 모자라요. 장소 추가로 채울 수 있어요"), warnings);
    }

    private AiDraftStop draft(int day, String time, int stay) {
        AiDraftStop stop = new AiDraftStop();
        stop.setDay(day);
        stop.setTime(time);
        stop.setStay(stay);
        return stop;
    }

    @Test
    void 끝_장소가_없으면_마지막_칸은_끝_시각에_안_잘림() {
        List<String[]> slots = ruleMaker.getSlotList(timed("19:00", "20:00"));
        assertEquals(1, slots.size());
        assertEquals("70", slots.get(0)[2]); // 저녁 70분 그대로 (예전엔 60분으로 잘림)
    }

    @Test
    void 끝_장소를_정했으면_그_시각에_맞춤() {
        PlanConditions conditions = timed("19:00", "20:00");
        FixedVisit finish = new FixedVisit();
        finish.setName("서울역");
        finish.setTime("20:00");
        conditions.setFinish(finish);
        List<String[]> slots = ruleMaker.getSlotList(conditions);
        assertEquals("60", slots.get(0)[2]);
        assertTrue(ruleMaker.isHardEnd(conditions, 1, 1));
        assertFalse(ruleMaker.isHardEnd(timed("19:00", "20:00"), 1, 1));
    }

    @Test
    void 마지막_칸만_끝_시간_없음으로_표시() {
        PlanGenerateResponse response = new PlanGenerateResponse();
        response.getStops().add(stop(1, "19:00", "아지양", null, false));
        response.getStops().add(stop(1, "20:40", "빈티지서울", null, false));
        response.getStops().add(stop(1, "22:30", "숙소 호텔", "숙소", false));
        response.getStops().add(stop(2, "12:00", "점심", null, false));
        response.getStops().add(stop(2, "19:00", "예약 식당", null, true));
        ruleMaker.markOpenEnds(response, timed("11:00", "22:00"), 2);
        assertEquals(List.of("1/20:40"), response.getOpenEndStops()); // 숙소 앞 칸만, 마지막이 예약이면 표시 안 함
    }

    @Test
    void 빈_시간과_이른_예약은_그_칸에_안내() {
        PlanConditions conditions = timed("11:00", "20:00");
        FixedVisit fixed = new FixedVisit();
        fixed.setName("어니언");
        fixed.setTime("09:00");
        conditions.setFixed(new ArrayList<>(List.of(fixed)));
        PlanGenerateResponse response = new PlanGenerateResponse();
        Stop onion = stop(1, "09:00", "어니언", null, true);
        onion.setStay(60);
        Stop cafe = stop(1, "14:00", "카페", null, false);
        cafe.setStay(70);
        Stop dinner = stop(1, "18:10", "저녁", null, false);
        response.getStops().addAll(List.of(onion, cafe, dinner));
        ruleMaker.addStopNotes(response, conditions);
        assertEquals(3, response.getStopNotes().size());
        assertEquals("14:00", response.getStopNotes().get(0).getTime()); // 10:00~14:00 빔
        assertEquals("warn", response.getStopNotes().get(0).getLevel());
        assertEquals("18:10", response.getStopNotes().get(1).getTime()); // 15:10~18:10 빔
        assertEquals("09:00", response.getStopNotes().get(2).getTime()); // 이른 예약
        assertEquals("info", response.getStopNotes().get(2).getLevel());
    }

    private Stop stop(int day, String time, String name, String category, boolean isFixed) {
        Stop stop = new Stop();
        stop.setDay(day);
        stop.setTime(time);
        stop.setName(name);
        stop.setCategory(category);
        stop.setFixed(isFixed);
        return stop;
    }

    @Test
    void 저녁_1차_2차가_끝_시각까지_나눠_씀() {
        List<String[]> slots = ruleMaker.getSlotList(timed("18:00", "22:00", "food", "cafe", "bar"));
        assertEquals(2, slots.size());
        assertEquals("18:00", slots.get(0)[1]);
        assertEquals("110", slots.get(0)[2]);          // 1차 18:00~19:50
        assertEquals("bar", slots.get(1)[0]);
        assertEquals("20:10", slots.get(1)[1]);
        assertEquals("110", slots.get(1)[2]);          // 2차 20:10~22:00
    }

    @Test
    void 이차가_150분_넘으면_그때만_삼차() {
        List<String[]> slots = ruleMaker.getSlotList(timed("18:00", "23:30", "food", "bar"));
        assertEquals(3, slots.size());
        assertEquals("120", slots.get(0)[2]);          // 1차는 120분까지
        assertEquals("bar", slots.get(2)[0]);
        int thirdEnd = toMin(slots.get(2)[1]) + Integer.parseInt(slots.get(2)[2]);
        assertEquals(23 * 60 + 30, thirdEnd);          // 3차가 끝 시각까지
    }

    @Test
    void 일박이일_저녁_시작이면_첫날도_고른_끝_시각까지() {
        PlanConditions conditions = timed("18:00", "22:00", "food", "bar");
        conditions.setEndDate("2026-11-20");
        assertEquals(22 * 60, ruleMaker.chosenDayEndMin(conditions, 1, 2));
        PlanConditions nextDayEnd = timed("18:00", "12:00", "food", "bar");
        nextDayEnd.setEndDate("2026-11-20");
        assertEquals(22 * 60, ruleMaker.chosenDayEndMin(nextDayEnd, 1, 2)); // 끝 시각이 다음 날 것이면 22:00
        assertEquals(20 * 60, ruleMaker.chosenDayEndMin(timed("10:00", "12:00"), 2, 3)); // 가운데 날은 20:00
    }

    @Test
    void 꼭_갈_카페가_저녁_시간이면_저녁_뒤로() {
        PlanRuleMaker maker = new PlanRuleMaker(null, new PlanCandidateFinder(null, null, null, null));
        PlanConditions conditions = timed("19:40", "21:40");
        Map<String, Place> candidates = new LinkedHashMap<>();
        candidates.put("c1", place("c1", "테디뵈르하우스", "cafe", "카페"));
        AiDraftStop cafe = new AiDraftStop();
        cafe.setDay(1);
        cafe.setId("c1");
        cafe.setName("테디뵈르하우스");
        cafe.setTime("19:40");
        cafe.setStay(90);
        maker.moveOutOfDinner(new ArrayList<>(List.of(cafe)), candidates, conditions, 1, "2026-11-19");
        assertEquals("21:10", cafe.getTime()); // 저녁 19:40~20:50 뒤 (끝 장소가 없으면 끝 시각을 넘어도 됨)
    }

    private int toMin(String time) {
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3));
    }

    private boolean isSight(String slotKinds) {
        String first = slotKinds.split(",")[0];
        return first.equals("tour") || first.equals("culture") || first.equals("shop");
    }
}
