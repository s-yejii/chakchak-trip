package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.trip.dto.Stop;

// 링크로 넣기 겹침: 그냥 넣어도 될 때도 남은 칸 중 첫 번째 같은 종류 칸과 [바꾸기]를 제안 (11/18)
// 지금 12:00 남도분식 → 남은 칸 13:30 동백양과점(카페) · 16:00 남산순환나들길 · 19:00 산청숯불가든(고정) · 20:30 카페 하나 더
class LinkSwapNextTest {

    // 겹침 찾기는 DB·경로를 안 써서 null로 만들어도 돼요
    private final LiveLinkServiceImpl liveLinkService = new LiveLinkServiceImpl(null, null, null, null);

    private final Stop dongbaek = stop(2L, "동백양과점 익선점", "카페,디저트", false, "maybe");
    private final Stop namsan = stop(3L, "남산순환나들길", "관광지", false, "maybe");
    private final Stop dinner = stop(4L, "산청숯불가든 을지로", "육류,고기요리", true, "maybe");
    private final Stop lateCafe = stop(5L, "밤 카페", "카페", false, "maybe");
    private final Stop newCafe = stop(null, "레인리포트 레인보우", "카페", false, "maybe");

    private Stop stop(Long id, String name, String category, boolean isFixed, String importance) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setName(name);
        stop.setCategory(category);
        stop.setFixed(isFixed);
        stop.setImportance(importance);
        return stop;
    }

    private AltStop altStop(Stop stop, String eta) {
        AltStop altStop = new AltStop();
        altStop.setId(stop.getId());
        altStop.setName(stop.getName());
        altStop.setFixed(stop.getFixed());
        altStop.setEta(eta);
        return altStop;
    }

    /** 그대로 넣기 결과: 여유가 많아서 새 카페가 하루 끝(21:40)에 들어감 */
    private Alternative makeBest() {
        Alternative best = new Alternative();
        best.getStops().add(altStop(dongbaek, "13:30"));
        best.getStops().add(altStop(namsan, "16:00"));
        best.getStops().add(altStop(dinner, "19:00"));
        best.getStops().add(altStop(lateCafe, "20:30"));
        best.getStops().add(altStop(newCafe, "21:40"));
        return best;
    }

    @Test
    void 그냥_넣어도_되면_첫_번째_카페와_겹침() {
        List<Stop> remaining = List.of(dongbaek, namsan, dinner, lateCafe);
        Map<Stop, Stop> swaps = liveLinkService.findOverlaps(List.of(newCafe), remaining, makeBest(), true);

        assertEquals(dongbaek, swaps.get(newCafe)); // 도착 시각이 가까운 밤 카페가 아니라 시간 순 첫 번째
    }

    @Test
    void 그냥_넣어도_되면_꼭_갈_곳은_건너뜀() {
        Stop mustCafe = stop(2L, "동백양과점 익선점", "카페,디저트", false, "must");
        List<Stop> remaining = List.of(mustCafe, namsan, dinner, lateCafe);
        Map<Stop, Stop> swaps = liveLinkService.findOverlaps(List.of(newCafe), remaining, makeBest(), true);

        assertEquals(lateCafe, swaps.get(newCafe));
    }

    @Test
    void 늦어지면_전처럼_도착_시각이_가장_가까운_칸() {
        List<Stop> remaining = List.of(dongbaek, namsan, dinner, lateCafe);
        Map<Stop, Stop> swaps = liveLinkService.findOverlaps(List.of(newCafe), remaining, makeBest(), false);

        assertEquals(lateCafe, swaps.get(newCafe));
    }
}
