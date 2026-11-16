package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.PlanArea;
import com.tripbiseo.plan.dto.PlanConditions;

// 구를 하나만 골랐으면 그 구 장소 먼저 (11/16: 강남구 1박 2일의 2일차가 성수로만 가던 문제)
class PlanRuleMakerGuTest {

    // 이 계산은 DB·다른 서비스를 안 써서 null로 만들어도 돼요
    private final PlanRuleMaker ruleMaker = new PlanRuleMaker(null, null);

    @Test
    void 지역_글자의_마지막_낱말이_구면_그_구() {
        PlanConditions conditions = new PlanConditions();
        conditions.setRegion("서울 강남구");
        assertEquals("강남구", ruleMaker.singleGuOf(conditions));
    }

    @Test
    void 지도로_구_하나를_골랐으면_그_구() {
        PlanConditions conditions = new PlanConditions();
        PlanArea area = new PlanArea();
        area.setName("송파구");
        conditions.setAreas(new ArrayList<>(List.of(area)));
        assertEquals("송파구", ruleMaker.singleGuOf(conditions));
    }

    @Test
    void 동이나_여러_구면_거르지_않음() {
        PlanConditions dong = new PlanConditions();
        dong.setRegion("서울 성동구 성수동");
        assertNull(ruleMaker.singleGuOf(dong));
        PlanConditions two = new PlanConditions();
        PlanArea first = new PlanArea();
        first.setName("중구");
        PlanArea second = new PlanArea();
        second.setName("종로구");
        two.setAreas(new ArrayList<>(List.of(first, second)));
        assertNull(ruleMaker.singleGuOf(two));
    }
}
