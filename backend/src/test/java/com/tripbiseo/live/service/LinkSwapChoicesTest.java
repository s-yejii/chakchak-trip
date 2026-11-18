package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.DemoClock;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.LinkFitRequest;
import com.tripbiseo.live.dto.LinkFitResponse;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * 링크로 넣기 [바꾸기] 선택지 (11/18): 식당 한 곳이면 바꿀 수 있는 식당마다 하나씩(시간 순, 맨 앞이 추천), 카페는 첫 번째 하나만.
 * 일정: 카페 10:00(60분) · A식당 12:00(60분) · 경복궁 14:00(60분) · 카페 16:00(40분) · B식당 19:00(60분). 이동은 모두 10분
 */
class LinkSwapChoicesTest {

    private LiveLinkServiceImpl liveLinkService;
    private Trip trip;

    @BeforeEach
    void setUp() {
        RouteClient routeClient = mock(RouteClient.class);
        Route route = new Route();
        route.setMinutes(10);
        when(routeClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), anyBoolean())).thenReturn(route);
        LiveStopFinder liveStopFinder = new LiveStopFinder();
        LiveTripReader liveTripReader = mock(LiveTripReader.class);
        liveLinkService = new LiveLinkServiceImpl(liveTripReader, liveStopFinder, new AlternativeCalculator(routeClient, liveStopFinder), null);

        trip = new Trip();
        trip.setStartDate(LocalDate.now(Geo.KST).toString()); // 오늘이 1일째
        trip.getStops().add(stop(1L, "온화커피", "카페", 37.5700, 126.9800, "10:00", 60));
        trip.getStops().add(stop(2L, "A식당", "한식", 37.5710, 126.9810, "12:00", 60));
        trip.getStops().add(stop(3L, "경복궁", "관광지", 37.5796, 126.9770, "14:00", 60));
        trip.getStops().add(stop(4L, "오후커피", "카페", 37.5720, 126.9820, "16:00", 40));
        trip.getStops().add(stop(5L, "B식당", "한식", 37.5730, 126.9830, "19:00", 60));
        when(liveTripReader.getOngoingTrip(anyLong(), anyLong(), any(), any())).thenReturn(trip);
    }

    @AfterEach
    void tearDown() {
        DemoClock.clear();
    }

    private Stop stop(Long id, String name, String category, double lat, double lng, String time, int stay) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setDay(1);
        stop.setName(name);
        stop.setCategory(category);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(false);
        stop.setState("todo");
        stop.setImportance("maybe");
        return stop;
    }

    /** 시연 시계로 "지금"을 오늘 hour:minute에 맞추고, 그 칸에 도착해 있게 함 */
    private void arriveAt(int hour, int minute, Stop current) {
        ZonedDateTime target = LocalDate.now(Geo.KST).atTime(hour, minute).atZone(Geo.KST);
        DemoClock.set(Duration.between(ZonedDateTime.now(Geo.KST), target).getSeconds());
        current.setArrivedAt(Geo.now().toLocalDate() + "T" + current.getTime() + ":00");
    }

    private LinkFitResponse fit(String name, String category) {
        LinkFitRequest.LinkPlace place = new LinkFitRequest.LinkPlace();
        place.setName(name);
        place.setCategory(category);
        place.setLat(37.5740);
        place.setLng(126.9840);
        LinkFitRequest request = new LinkFitRequest();
        request.setLat(37.5);  // 지금 있는 칸은 도착 기록으로 찾음
        request.setLng(126.9);
        request.setPlaces(List.of(place));
        return liveLinkService.fitPlace(1L, 10L, request);
    }

    private List<Alternative> swapOptions(LinkFitResponse response) {
        List<Alternative> swaps = new ArrayList<>();
        for (Alternative option : response.getAlternatives()) {
            if ("바꾸기".equals(option.getShortName())) {
                swaps.add(option);
            }
        }
        return swaps;
    }

    @Test
    void 식당_링크는_남은_식당마다_바꾸기가_뜸() {
        arriveAt(11, 0, trip.getStops().get(0)); // 11:00 온화커피

        LinkFitResponse response = fit("새 식당", "국밥");
        List<Alternative> swaps = swapOptions(response);

        assertEquals(2, swaps.size());
        assertEquals("A식당 → 새 식당", swaps.get(0).getTitle());
        assertEquals("B식당 → 새 식당", swaps.get(1).getTitle());
        assertEquals(swaps.get(0), response.getAlternatives().get(0)); // 시간 순 첫 번째가 맨 앞
        assertTrue(swaps.get(0).isRecommended());
        assertEquals("식당 2곳과 겹쳐요", response.getMessage());
    }

    @Test
    void 점심이_지났으면_저녁_식당_하나만() {
        trip.getStops().get(0).setState("done");
        trip.getStops().get(1).setState("done"); // A식당 다녀옴
        arriveAt(14, 30, trip.getStops().get(2)); // 14:30 경복궁

        LinkFitResponse response = fit("새 식당", "국밥");
        List<Alternative> swaps = swapOptions(response);

        assertEquals(1, swaps.size());
        assertEquals("B식당 → 새 식당", swaps.get(0).getTitle());
        assertEquals("B식당과(와) 겹쳐요", response.getMessage());
    }

    @Test
    void 카페_링크는_첫_번째_카페_하나만() {
        trip.getStops().get(0).setState("done");
        arriveAt(12, 10, trip.getStops().get(1)); // 12:10 A식당
        trip.getStops().add(stop(6L, "밤커피", "카페", 37.5735, 126.9835, "20:30", 40)); // 남은 카페: 16:00 오후커피 · 20:30 밤커피

        LinkFitResponse response = fit("새 카페", "카페,디저트");
        List<Alternative> swaps = swapOptions(response);

        assertEquals(1, swaps.size());
        assertEquals("오후커피 → 새 카페", swaps.get(0).getTitle());
    }
}
