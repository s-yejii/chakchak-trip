package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Place;

// 비슷한 가게 [다음 고정 일정 가는 길] 정렬 (11/17 ③, ④ 예지 OK). 숫자는 도커 DB 실제 값 (스시도 → 동서울 사이 일식)
class SortOnRouteTest {

    private final Map<String, Integer> detours = new HashMap<>();

    private Place place(String name, double rating, int reviews, int detour) {
        Place place = new Place();
        place.setId(name);
        place.setName(name);
        place.setRating(rating);
        place.setReviewCount(reviews);
        detours.put(name, detour);
        return place;
    }

    @Test
    void 검증된_가게를_덜_돌아가는_순으로_맨_앞() {
        List<Place> places = new ArrayList<>();
        places.add(place("무진", 4.99, 113, 36));          // 평점은 제일 높지만 리뷰 113개
        places.add(place("산고 스시바", 4.92, 3641, 1177));
        places.add(place("스시이세이", 4.92, 2064, 518));
        places.add(place("토오코", 4.90, 1323, 1216));
        places.add(place("스시 스이신", 4.91, 806, 935));
        LiveServiceImpl.sortOnRoute(places, detours);
        assertEquals("스시이세이", places.get(0).getName());
        assertEquals("산고 스시바", places.get(1).getName());
        assertEquals("토오코", places.get(2).getName());
        assertEquals("스시 스이신", places.get(3).getName()); // 나머지는 평점 × log(리뷰 수): 806개 > 113개
        assertEquals("무진", places.get(4).getName());
    }
}
