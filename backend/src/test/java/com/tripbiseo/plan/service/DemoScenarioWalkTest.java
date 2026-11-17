package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.trip.dto.Stop;

// 시연 일정 이동 수단 (11/17): 나들길 → 팔각정, 팔각정 → 회식은 걷기로 고정해 대본 숫자가 매번 같게
class DemoScenarioWalkTest {

    private Stop find(PlanGenerateResponse plan, String name) {
        for (Stop stop : plan.getStops()) {
            if (name.equals(stop.getName())) {
                return stop;
            }
        }
        return null;
    }

    @Test
    void 팔각정과_회식은_걸어서() {
        PlanConditions conditions = new PlanConditions();
        conditions.setStartDate("2026-11-16");
        PlanGenerateResponse plan = new DemoScenario().makeDraft(conditions);
        assertEquals("walk", find(plan, "남산 팔각정").getMode());
        assertEquals("walk", find(plan, "산청숯불가든 을지로").getMode());
        assertNull(find(plan, "남산순환나들길").getMode()); // 숙소 → 나들길은 그대로 (서버가 고름)
    }

    @Test
    void 숙소_칸은_관광공사_르메르디앙_장소로_사진이_나옴() {
        PlanConditions conditions = new PlanConditions();
        conditions.setStartDate("2026-11-16");
        int count = 0;
        for (Stop stop : new DemoScenario().makeDraft(conditions).getStops()) {
            if ("르메르디앙 서울 명동".equals(stop.getName())) {
                assertEquals("tour-3341540", stop.getPlaceId());
                count++;
            }
        }
        assertEquals(3, count); // 1일차 낮·밤, 2일차 아침
    }
}
