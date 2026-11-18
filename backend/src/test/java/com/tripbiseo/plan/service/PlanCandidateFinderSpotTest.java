package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Place;

// 11/18 사용성 점검: 타는 곳·추모 공간·지나가는 곳을 여가 후보로 넣지 않기
class PlanCandidateFinderSpotTest {

    // 이름·분류만 보는 검사라 DB·카카오는 null로 만들어도 돼요
    private final PlanCandidateFinder finder = new PlanCandidateFinder(null, null, null, null);

    private Place place(String id, String name, String kind, String category) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setKind(kind);
        place.setCategory(category);
        return place;
    }

    @Test
    void 선착장_정류장은_머무는_장소가_아님() {
        assertTrue(finder.isTransitSpot(place("hangang-seoulforest", "한강버스 서울숲선착장", "tour", "한강버스 선착장")));
        assertTrue(finder.isTransitSpot(place("tour-1", "광화문 버스정류장", "tour", "관광지")));
        assertFalse(finder.isTransitSpot(place("tour-2", "서울숲", "tour", "관광지")));
    }

    @Test
    void 추모_공간은_종류_말로_거르고_이름에_기억만_있으면_그대로() {
        assertTrue(finder.isMemorialName("기억의 터(일본군 '위안부')"));
        assertTrue(finder.isMemorialName("서울현충원"));
        assertTrue(finder.isMemorialName("세월호 참사 추모공간"));
        assertFalse(finder.isMemorialName("향기억"));
        assertFalse(finder.isMemorialName("남산골한옥마을"));
    }

    @Test
    void 광장_계단은_지나가는_볼거리() {
        assertTrue(finder.isPassThroughSpot(place("t1", "코엑스동측광장", "tour", "관광지")));
        assertTrue(finder.isPassThroughSpot(place("t2", "후암동108계단", "tour", "관광지")));
        assertFalse(finder.isPassThroughSpot(place("t3", "봉은사(서울)", "tour", "관광지")));
    }
}
