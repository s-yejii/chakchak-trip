package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tripbiseo.live.dto.AskedPlace;

// AI에게 묻기 결과에서 보정 평점 4.0 미만은 뺌 (11/17 리허설: "제일 가까운 카페"로 3.43점 가게)
class AskLowRatingTest {

    private AskedPlace placeOf(Double rating) {
        AskedPlace place = new AskedPlace();
        place.setRating(rating);
        return place;
    }

    @Test
    void 평점_4점_미만이면_뺌() {
        assertTrue(PlaceAskServiceImpl.isLowRated(placeOf(3.43))); // 플랫슬리퍼 명동점
        assertFalse(PlaceAskServiceImpl.isLowRated(placeOf(4.14))); // 우지커피 을지로3가역점
        assertFalse(PlaceAskServiceImpl.isLowRated(placeOf(4.0)));
    }

    @Test
    void 평점을_모르면_빼지_않음() {
        assertFalse(PlaceAskServiceImpl.isLowRated(placeOf(null)));
    }
}
