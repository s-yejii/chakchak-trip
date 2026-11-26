package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

import lombok.RequiredArgsConstructor;

/**
 * ④ 대안 계산기: 바뀐 칸 목록을 넣으면 몇 시에 도착하는지 계산해요.
 *  - 칸마다 이동 시간과 도착 시각, 지도에 그릴 경로선
 *  - 기준 칸(첫 고정 일정, 없으면 마지막 칸)에 도착하는 시각과 여유
 * 고정 일정 = 시각을 꼭 지켜야 하는 예약 칸(KTX·회식 등).
 * 여유(분) = 기준 칸 계획 시각 − 예상 도착. 음수면 지각이에요.
 * AlternativeServiceImpl 안에서만 쓰는 도우미라 인터페이스 없이 @Component로 둠 (LiveAlertMaker와 같은 방식).
 */
@Component // Spring이 객체를 하나 만들어 두고, 필요한 곳에 생성자 주입으로 넣어 줌
@RequiredArgsConstructor
public class AlternativeCalculator {

    private final RouteClient routeClient;       // 이동 시간. 후보를 여러 개 비교할 때는 isPrecise=false(거리로 어림)
    private final LiveStopFinder liveStopFinder; // 출발·시작 시각, 머무는 시간 (④ 도우미)

    /** 바뀐 칸 목록으로 대안 하나 계산. 이동 시간은 전부 거리로 어림해요 (길찾기 API 안 부름) */
    public Alternative calculate(Trip trip, Stop current, List<Stop> stops, double lat, double lng) {
        return calculate(trip, current, stops, lat, lng, 0);
    }

    /**
     * 위와 같지만 앞쪽 preciseCount 구간은 진짜 길찾기 API로 계산해요.
     * 직접 고친 일정(/live/preview)도 여행 중 화면(/live/status)과 같은 방식이어야 두 화면 숫자가 같아요.
     */
    public Alternative calculate(Trip trip, Stop current, List<Stop> stops, double lat, double lng, int preciseCount) {
        return calculate(trip, current, stops, lat, lng, preciseCount, false);
    }

    /**
     * 위와 같지만 isWaitingPlanned가 true면 원래 칸(id 있음)은 일찍 도착해도 계획 시각까지 기다렸다 시작해요 (eta도 그 시각).
     * 늦게 도착하면 도착하자마자 시작해요.
     * 링크로 넣기(/live/link-fit)만 써요: 새 장소를 넣어도 원래 칸 사이 빈 시간이 그대로 남게.
     */
    public Alternative calculate(Trip trip, Stop current, List<Stop> stops, double lat, double lng, int preciseCount, boolean isWaitingPlanned) {
        // 1. 출발점과 출발 시각
        //    - 어떤 칸에 있으면: 그 칸에서, 머무는 시간이 끝날 때
        //    - 이동 중이면: 내 위치에서, 지금
        double fromLat = lat;
        double fromLng = lng;
        int clock = Geo.nowMinutes();
        if (current != null) {
            fromLat = current.getLat();
            fromLng = current.getLng();
            clock = Math.max(clock, liveStopFinder.getLeaveMinutes(current));
        }

        // 2. 칸마다 이동 시간과 도착 시각 (앞 preciseCount 구간만 길찾기 API, 나머지는 거리로 어림)
        Alternative alternative = new Alternative();
        List<Integer> arriveList = new ArrayList<>();
        for (Stop stop : stops) {
            boolean isPrecise = arriveList.size() < preciseCount;
            Route route = routeClient.getRoute(fromLat, fromLng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), isPrecise);
            int arrive = clock + route.getMinutes();
            int start = getStartMinutes(stop, arrive, isWaitingPlanned);
            arriveList.add(arrive);
            alternative.getStops().add(toAltStop(stop, start, route.getMinutes()));
            if (route.getCoords() != null) {
                alternative.getCoords().addAll(route.getCoords());
            }
            clock = start + liveStopFinder.getStay(stop);
            fromLat = stop.getLat();
            fromLng = stop.getLng();
        }

        // 3. 기준 칸에 몇 시에 도착하고, 여유가 몇 분인지
        setFinalArrive(alternative, stops, arriveList);
        return alternative;
    }

    /** 칸 목록 복사본 (대안마다 따로 고쳐도 원본이 안 바뀌게) */
    public List<Stop> copyStops(List<Stop> stops) {
        List<Stop> copies = new ArrayList<>();
        for (Stop stop : stops) {
            copies.add(copyStop(stop));
        }
        return copies;
    }

    // ---------------------------------------------------------------- 도우미

    /** 칸에서 시작하는 시각. 고정 일정은 일찍 와도 계획 시각에 시작하고, isWaitingPlanned면 원래 칸(id 있음)도 그래요 */
    private int getStartMinutes(Stop stop, int arrive, boolean isWaitingPlanned) {
        int start = liveStopFinder.getStartMinutes(stop, arrive);
        if (isWaitingPlanned && stop.getId() != null && stop.getTime() != null) {
            start = Math.max(start, Geo.toMinutes(stop.getTime()));
        }
        return start;
    }

    /** 기준 칸의 도착 시각과 여유를 적어요. 예: 18:00 예약에 17:50 도착 → 여유 10분 */
    private void setFinalArrive(Alternative alternative, List<Stop> stops, List<Integer> arriveList) {
        int basisIndex = stops.size() - 1;
        for (int i = 0; i < stops.size(); i++) {
            if (Boolean.TRUE.equals(stops.get(i).getFixed())) {
                basisIndex = i;
                break;
            }
        }
        int arrive = arriveList.get(basisIndex);
        alternative.setFinalArrive(Geo.toHhmm(arrive));
        alternative.setSlackMin(Geo.toMinutes(stops.get(basisIndex).getTime()) - arrive);
    }

    /**
     * Stop → 화면용 AltStop.
     * eta = 그 칸에서 시작하는 시각 (기다리지 않으면 예상 도착과 같음). 고정 일정은 계획 시각 그대로예요 (API.md).
     * change(바뀐 점)는 대안마다 따로 채워요.
     */
    private AltStop toAltStop(Stop stop, int start, int moveMin) {
        AltStop altStop = new AltStop();
        altStop.setId(stop.getId());
        altStop.setPlaceId(stop.getPlaceId());
        altStop.setName(stop.getName());
        altStop.setCategory(stop.getCategory());
        altStop.setLat(stop.getLat());
        altStop.setLng(stop.getLng());
        altStop.setStay(stop.getStay());
        altStop.setFixed(Boolean.TRUE.equals(stop.getFixed()));
        altStop.setImportance(stop.getImportance());
        altStop.setEta(Boolean.TRUE.equals(stop.getFixed()) ? stop.getTime() : Geo.toHhmm(start));
        altStop.setMoveMin(moveMin);
        altStop.setClosingSoon(false);
        return altStop;
    }

    /** 칸 하나 복사본 */
    private Stop copyStop(Stop stop) {
        Stop copy = new Stop();
        copy.setId(stop.getId());
        copy.setTripId(stop.getTripId());
        copy.setDay(stop.getDay());
        copy.setPlaceId(stop.getPlaceId());
        copy.setName(stop.getName());
        copy.setCategory(stop.getCategory());
        copy.setAddress(stop.getAddress());
        copy.setLat(stop.getLat());
        copy.setLng(stop.getLng());
        copy.setTime(stop.getTime());
        copy.setStay(stop.getStay());
        copy.setFixed(stop.getFixed());
        copy.setMode(stop.getMode());
        copy.setState(stop.getState());
        copy.setImportance(stop.getImportance());
        return copy;
    }
}
