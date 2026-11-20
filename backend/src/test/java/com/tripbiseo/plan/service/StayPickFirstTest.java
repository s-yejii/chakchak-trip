package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbiseo.plan.dto.StayPick;

// 11/20: 시연 숙소는 맨 위, 목록에 이미 있으면 한 번만, 수는 5곳 그대로
class StayPickFirstTest {

    private final StayPickServiceImpl service = new StayPickServiceImpl(null, null, null);

    private StayPick stay(String id) {
        StayPick stay = new StayPick();
        stay.setPlaceId(id);
        return stay;
    }

    private List<StayPick> list(String... ids) {
        List<StayPick> list = new ArrayList<>();
        for (String id : ids) {
            list.add(stay(id));
        }
        return list;
    }

    private String idsOf(List<StayPick> list) {
        StringBuilder ids = new StringBuilder();
        for (StayPick stay : list) {
            ids.append(stay.getPlaceId()).append(' ');
        }
        return ids.toString().trim();
    }

    @Test
    void 목록에_있으면_맨_위로_옮겨요() {
        assertEquals("c a b d e", idsOf(service.putFirst(stay("c"), list("a", "b", "c", "d", "e"))));
    }

    @Test
    void 목록에_없으면_맨_위에_넣고_다섯_곳까지() {
        assertEquals("x a b c d", idsOf(service.putFirst(stay("x"), list("a", "b", "c", "d", "e"))));
    }

    @Test
    void 시연_숙소를_못_찾으면_그대로() {
        assertEquals("a b", idsOf(service.putFirst(null, list("a", "b"))));
    }
}
