package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.DemoClock;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.LinkFitRequest;
import com.tripbiseo.live.dto.LinkFitResponse;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * 링크로 넣기: 원래 칸은 일찍 도착하면 계획 시각까지 기다렸다 시작 (11/18)
 * 지금 12:05, 남도분식(12:00 도착, 80분) → 동백양과점 13:30(60분) · 르메르디앙 15:00(45분) · 나들길 16:00(50분)
 * · 팔각정 17:00(30분) · 산청숯불가든 19:00(고정). 이동은 모두 10분 (레인리포트만 따로 정함)
 */
class LinkFitWaitTest {

    private static final double RAIN_LAT = 37.5440;
    private static final double RAIN_LNG = 127.0560;

    private RouteClient routeClient;
    private LiveLinkServiceImpl liveLinkService;
    private Trip trip;

    @BeforeEach
    void setUp() {
        // 시연 시계로 "지금"을 오늘 12:05에 맞춤 (테스트를 언제 돌려도 같은 결과)
        ZonedDateTime noon = LocalDate.now(Geo.KST).atTime(12, 5).atZone(Geo.KST);
        DemoClock.set(Duration.between(ZonedDateTime.now(Geo.KST), noon).getSeconds());

        routeClient = mock(RouteClient.class);
        when(routeClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), anyBoolean())).thenReturn(route(10));
        LiveStopFinder liveStopFinder = new LiveStopFinder();
        AlternativeCalculator alternativeCalculator = new AlternativeCalculator(routeClient, liveStopFinder);
        LiveTripReader liveTripReader = mock(LiveTripReader.class);
        liveLinkService = new LiveLinkServiceImpl(liveTripReader, liveStopFinder, alternativeCalculator, null); // 장소 id가 없어서 영업 확인 안 함

        trip = new Trip();
        trip.setStartDate(Geo.now().toLocalDate().toString()); // 오늘이 1일째
        Stop bunsik = stop(1L, "남도분식 익선점", "분식", 37.5731, 126.9901, "12:00", 80, false);
        bunsik.setArrivedAt(Geo.now().toLocalDate() + "T12:00:00"); // 지금 여기 있음
        trip.getStops().add(bunsik);
        trip.getStops().add(stop(2L, "동백양과점 익선점", "카페,디저트", 37.5734, 126.9895, "13:30", 60, false));
        trip.getStops().add(stop(3L, "르메르디앙 서울 명동", "숙소", 37.5610, 126.9850, "15:00", 45, false));
        trip.getStops().add(stop(4L, "남산순환나들길", "관광지", 37.5552, 126.9865, "16:00", 50, false));
        trip.getStops().add(stop(5L, "남산 팔각정", "관광지", 37.5516, 126.9876, "17:00", 30, false));
        trip.getStops().add(stop(6L, "산청숯불가든 을지로", "육류,고기요리", 37.5658, 126.9906, "19:00", 120, true));
        when(liveTripReader.getOngoingTrip(anyLong(), anyLong(), any(), any())).thenReturn(trip);
    }

    @AfterEach
    void tearDown() {
        DemoClock.clear();
    }

    private Stop stop(Long id, String name, String category, double lat, double lng, String time, int stay, boolean isFixed) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setDay(1);
        stop.setName(name);
        stop.setCategory(category);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setState("todo");
        stop.setImportance("maybe");
        return stop;
    }

    private Route route(int minutes) {
        Route route = new Route();
        route.setMinutes(minutes);
        return route;
    }

    /** 레인리포트로 가는 이동 시간을 정하고 link-fit → [바꾸기] 일정 */
    private Alternative fitRainAndSwap(int minutesToRain) {
        when(routeClient.getRoute(anyDouble(), anyDouble(), eq(RAIN_LAT), eq(RAIN_LNG), any(), any(), anyBoolean())).thenReturn(route(minutesToRain));
        LinkFitRequest.LinkPlace place = new LinkFitRequest.LinkPlace();
        place.setName("레인리포트 레인보우");
        place.setCategory("카페");
        place.setLat(RAIN_LAT);
        place.setLng(RAIN_LNG);
        LinkFitRequest request = new LinkFitRequest();
        request.setLat(37.5731); // 남도분식에 있음
        request.setLng(126.9901);
        request.setPlaces(List.of(place));

        LinkFitResponse response = liveLinkService.fitPlace(1L, 10L, request);
        for (Alternative option : response.getAlternatives()) {
            if ("바꾸기".equals(option.getShortName())) {
                return option;
            }
        }
        return null;
    }

    private String etaOf(Alternative option, String name) {
        for (AltStop stop : option.getStops()) {
            if (name.equals(stop.getName())) {
                return stop.getEta();
            }
        }
        return null;
    }

    @Test
    void 일찍_도착하면_원래_칸은_계획_시각_그대로() {
        // 13:20 출발 → 레인리포트 13:30~14:30 → 르메르디앙 14:40 도착, 15:00까지 기다림
        Alternative swap = fitRainAndSwap(10);

        assertNotNull(swap);
        assertEquals("13:30", etaOf(swap, "레인리포트 레인보우"));
        assertEquals("15:00", etaOf(swap, "르메르디앙 서울 명동"));
        assertEquals("16:00", etaOf(swap, "남산순환나들길"));
        assertEquals("17:00", etaOf(swap, "남산 팔각정"));
    }

    @Test
    void 늦게_도착하면_도착하자마자_시작해서_뒤로_밀림() {
        // 레인리포트까지 60분: 14:20~15:20 → 르메르디앙 15:30(45분) → 나들길 16:25(50분) → 팔각정 17:25
        Alternative swap = fitRainAndSwap(60);

        assertNotNull(swap);
        assertEquals("15:30", etaOf(swap, "르메르디앙 서울 명동"));
        assertEquals("16:25", etaOf(swap, "남산순환나들길"));
        assertEquals("17:25", etaOf(swap, "남산 팔각정"));
    }
}
