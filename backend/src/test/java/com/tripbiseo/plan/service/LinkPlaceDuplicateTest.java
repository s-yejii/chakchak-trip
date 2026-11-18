package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.trip.dto.Place;

// 링크 장소 중복: 같은 가게가 DB(id 있음)·카카오(id 없음)로 두 번 찾아져도 카드는 하나 (11/18)
class LinkPlaceDuplicateTest {

    // 중복 거르기는 Gemini·DB를 안 써서 null로 만들어도 돼요
    private final LinkServiceImpl linkService = new LinkServiceImpl(null, null, null, null, null, null);

    private Place makePlace(String id, String name, String category, double lat, double lng) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setCategory(category);
        place.setLat(lat);
        place.setLng(lng);
        return place;
    }

    @Test
    void 카카오가_먼저면_그_자리를_DB_장소로_바꿈() {
        List<Place> added = new ArrayList<>();
        linkService.addPlace(added, makePlace("naver-1", "남도분식 익선점", "분식", 37.5731, 126.9901));
        linkService.addPlace(added, makePlace(null, "레인리포트 레인보우", "카페", 37.5440, 127.0560));
        linkService.addPlace(added, makePlace("naver-2", "레인리포트레인보우", "카페,디저트", 37.5442, 127.0561)); // 약 24m

        assertEquals(2, added.size());
        assertEquals("naver-2", added.get(1).getId()); // 두 번째 자리 그대로, DB 것으로
    }

    @Test
    void DB가_먼저면_카카오_것은_안_넣음() {
        List<Place> added = new ArrayList<>();
        linkService.addPlace(added, makePlace("naver-2", "레인리포트 레인보우", "카페,디저트", 37.5442, 127.0561));
        linkService.addPlace(added, makePlace(null, "레인리포트", "카페", 37.5440, 127.0560)); // 한 이름이 다른 이름을 포함

        assertEquals(1, added.size());
        assertEquals("naver-2", added.get(0).getId());
    }

    @Test
    void 이름이_비슷해도_50m_넘게_떨어지면_다른_지점() {
        List<Place> added = new ArrayList<>();
        linkService.addPlace(added, makePlace(null, "레인리포트 레인보우", "카페", 37.5440, 127.0560));
        linkService.addPlace(added, makePlace("naver-3", "레인리포트 레인보우 2호점", "카페", 37.5480, 127.0560)); // 약 440m

        assertEquals(2, added.size());
    }
}
