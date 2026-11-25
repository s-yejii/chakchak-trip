package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.DayCheckPlace;
import com.tripbiseo.plan.dto.DayCheckResponse;

// 11/20: 강남·종로·성수·연남 8곳 (묶음 번호는 DB에서 ST_ClusterDBSCAN eps 1500m로 받은 값 그대로)
class DayCheckTest {

    private final DayCheckServiceImpl service = new DayCheckServiceImpl(null, null);

    private DayCheckPlace place(String name, String category, double lat, double lng, int clusterNo) {
        DayCheckPlace place = new DayCheckPlace();
        place.setName(name);
        place.setCategory(category);
        place.setLat(lat);
        place.setLng(lng);
        place.setClusterNo(clusterNo);
        return place;
    }

    private List<DayCheckPlace> eight() {
        List<DayCheckPlace> list = new ArrayList<>();
        list.add(place("고수닭갈비", "닭갈비", 37.4997744, 127.0281716, 3));
        list.add(place("뉴욕러브베이글 강남본점", "베이글", 37.5026122, 127.026386, 3));
        list.add(place("솥내음 종로인사동점", "한식", 37.5721281, 126.9870311, 2));
        list.add(place("잘빠진메밀 서순라길점", "막국수", 37.5740448, 126.9915043, 2));
        list.add(place("성수다락", "양식", 37.5398955, 127.0562588, 0));
        list.add(place("소문난성수감자탕", "감자탕", 37.542837, 127.054387, 0));
        list.add(place("랜디스도넛 연남점", "도넛", 37.5620628, 126.9242188, 1));
        list.add(place("카페 공명 연남점", "카페,디저트", 37.5600309, 126.9264344, 1));
        return list;
    }

    @Test
    void 네_동네_여덟_곳은_9시간이면_넘어요() {
        DayCheckResponse response = service.makeResponse(eight(), 540);
        assertEquals(4, response.getGroups());
        assertEquals(470, response.getStayMin()); // 카페 1곳 50분 + 7곳 60분
        assertTrue(response.isOver());
    }

    @Test
    void 열두_시간이면_하루에_들어가요() {
        assertFalse(service.makeResponse(eight(), 720).isOver());
    }
}
