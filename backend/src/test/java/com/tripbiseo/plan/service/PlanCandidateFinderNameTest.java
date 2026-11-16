package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// 꼭 갈 곳 이름 맞추기: 말한 이름 뒤에 더 붙은 다른 곳(박물관 안 정원 등)으로 바뀌지 않게 (11/14)
class PlanCandidateFinderNameTest {

    // 이름 비교는 DB·카카오를 안 써서 null로 만들어도 돼요
    private final PlanCandidateFinder finder = new PlanCandidateFinder(null, null, null, null);

    @Test
    void 박물관_안_정원은_박물관이_아님() {
        assertTrue(finder.isPartOf("국립중앙박물관 전통염료식물원", "국립중앙박물관"));
    }

    @Test
    void 띄어쓰기만_다르면_같은_곳() {
        assertFalse(finder.isPartOf("여의도한강공원", "여의도 한강공원"));
    }

    @Test
    void 찾은_이름이_더_짧으면_아님() {
        assertFalse(finder.isPartOf("남도분식", "남도분식 익선점"));
    }

    @Test
    void 이름이_달라도_서로_포함은_그대로_같은_곳() {
        assertTrue(finder.isSameName("국립중앙박물관 전통염료식물원", "국립중앙박물관")); // 예전 기준은 그대로 (다른 곳에서 씀)
    }

    @Test
    void 박물관_안_시설은_꼭_갈_곳으로_안_셈_지점은_셈() {
        assertTrue(finder.isInnerSpot("국립중앙박물관 전통염료식물원", "국립중앙박물관"));
        assertFalse(finder.isInnerSpot("남도분식 익선점", "남도분식"));
    }
}
