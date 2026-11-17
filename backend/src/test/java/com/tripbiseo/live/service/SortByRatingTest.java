package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Place;

// 비슷한 가게 [다음 고정 일정 가는 길]: 평점 높은 순, 평점 없는 곳은 뒤로 (11/17 ③, ④ 예지 OK)
class SortByRatingTest {

    private Place place(String name, Double rating) {
        Place place = new Place();
        place.setName(name);
        place.setRating(rating);
        return place;
    }

    @Test
    void 평점_높은_순_없는_곳은_뒤로() {
        List<Place> places = new ArrayList<>();
        places.add(place("카사나리", 4.2));
        places.add(place("평점없음", null));
        places.add(place("스시이세이", 4.6));
        places.add(place("훈지", 4.4));
        LiveServiceImpl.sortByRating(places);
        assertEquals("스시이세이", places.get(0).getName());
        assertEquals("훈지", places.get(1).getName());
        assertEquals("카사나리", places.get(2).getName());
        assertEquals("평점없음", places.get(3).getName());
    }
}
