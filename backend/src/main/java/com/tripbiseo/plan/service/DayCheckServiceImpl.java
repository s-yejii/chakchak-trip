package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.dev.DevService;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.DayCheckPlace;
import com.tripbiseo.plan.dto.DayCheckRequest;
import com.tripbiseo.plan.dto.DayCheckResponse;
import com.tripbiseo.plan.mapper.DayCheckMapper;

import lombok.RequiredArgsConstructor;

/**
 * ③ 하루에 다 갈 수 있는지 (11/20 피드백 "멀리 떨어진 곳이면 며칠 필요한지").
 * PostGIS ST_ClusterDBSCAN으로 1.5km 안으로 이어진 장소끼리 한 동네로 묶고 (DayCheckMapper.xml),
 * 필요한 시간 = 머무는 시간(업종 기본값) + 동네 안 걷기 + 동네 옮기기(대중교통 어림: 15분 + 1km에 3분).
 * 길찾기 API는 부르지 않아요 (일정 만들기 전 안내용 어림, 실제 일정은 일정 만들기가 길찾기로 맞춰요).
 * 이미 손에 있는 두 점 사이 거리는 Geo.distanceMeters로 (CLAUDE.md 거리 계산 규칙)
 */
@Service
@RequiredArgsConstructor
public class DayCheckServiceImpl implements DayCheckService {

    static final int CLUSTER_M = 1500;      // 이 안으로 이어지면 같은 동네
    static final double WALK_M_PER_MIN = 67; // 4km/h
    static final int TRANSIT_BASE_MIN = 15; // 동네 옮길 때 역까지 걷기·기다리기·갈아타기
    static final int TRANSIT_MIN_PER_KM = 3;
    static final int CAFE_STAY_MIN = 50;
    static final int OTHER_STAY_MIN = 60;
    private static final int MAX_PLACES = 30;
    private static final String CAFE_WORDS = ".*(카페|커피|디저트|베이커리|제과|빵).*";

    private final DayCheckMapper dayCheckMapper;
    private final DevService devService;

    @Override
    public DayCheckResponse checkDay(long memberId, DayCheckRequest request) {
        List<String> ids = request.getPlaceIds() == null ? List.of() : request.getPlaceIds();
        if (ids.size() < 2 || ids.size() > MAX_PLACES || devService.isDemoAccount(memberId)) {
            DayCheckResponse empty = new DayCheckResponse();
            empty.setDayMin(request.getDayMinutes());
            return empty; // 한 곳 이하·시연 계정은 묻지 않아요 (시연은 시나리오 일정)
        }
        return makeResponse(dayCheckMapper.selectPlaceClusterList(ids, CLUSTER_M), request.getDayMinutes());
    }

    /** 묶음 번호별로 나눠 분을 셈 (DB 없이 테스트할 수 있게 따로) */
    DayCheckResponse makeResponse(List<DayCheckPlace> places, int dayMinutes) {
        Map<Integer, List<DayCheckPlace>> clusters = new TreeMap<>();
        int stayMin = 0;
        for (DayCheckPlace place : places) {
            clusters.computeIfAbsent(place.getClusterNo(), key -> new ArrayList<>()).add(place);
            stayMin += stayOf(place);
        }
        int walkMin = 0;
        List<double[]> centers = new ArrayList<>();
        DayCheckResponse response = new DayCheckResponse();
        for (List<DayCheckPlace> cluster : clusters.values()) {
            walkMin += walkInside(cluster);
            centers.add(centerOf(cluster));
            List<String> names = new ArrayList<>();
            for (DayCheckPlace place : cluster) {
                names.add(place.getName());
            }
            response.getGroupNames().add(names);
        }
        int moveMin = moveBetween(centers);
        response.setGroups(clusters.size());
        response.setStayMin(stayMin);
        response.setWalkMin(walkMin);
        response.setMoveMin(moveMin);
        response.setNeedMin(stayMin + walkMin + moveMin);
        response.setDayMin(dayMinutes);
        response.setOver(places.size() >= 2 && response.getNeedMin() > dayMinutes);
        return response;
    }

    private int stayOf(DayCheckPlace place) {
        String category = place.getCategory() == null ? "" : place.getCategory();
        return category.matches(CAFE_WORDS) ? CAFE_STAY_MIN : OTHER_STAY_MIN;
    }

    private double[] centerOf(List<DayCheckPlace> cluster) {
        double lat = 0;
        double lng = 0;
        for (DayCheckPlace place : cluster) {
            lat += place.getLat();
            lng += place.getLng();
        }
        return new double[] { lat / cluster.size(), lng / cluster.size() };
    }

    // 동네 안: 가까운 곳부터 차례로 걸을 때 걷는 분
    private int walkInside(List<DayCheckPlace> cluster) {
        List<DayCheckPlace> left = new ArrayList<>(cluster);
        DayCheckPlace current = left.remove(0);
        double meters = 0;
        while (!left.isEmpty()) {
            DayCheckPlace nearest = left.get(0);
            for (DayCheckPlace place : left) {
                if (distance(current, place) < distance(current, nearest)) {
                    nearest = place;
                }
            }
            meters += distance(current, nearest);
            left.remove(nearest);
            current = nearest;
        }
        return (int) Math.round(meters / WALK_M_PER_MIN);
    }

    // 동네 사이: 가까운 동네부터 차례로 옮길 때 대중교통 어림 분
    private int moveBetween(List<double[]> centers) {
        if (centers.isEmpty()) {
            return 0;
        }
        List<double[]> left = new ArrayList<>(centers);
        double[] current = left.remove(0);
        int minutes = 0;
        while (!left.isEmpty()) {
            double[] nearest = left.get(0);
            for (double[] center : left) {
                if (distance(current, center) < distance(current, nearest)) {
                    nearest = center;
                }
            }
            minutes += TRANSIT_BASE_MIN + (int) Math.round(distance(current, nearest) / 1000 * TRANSIT_MIN_PER_KM);
            left.remove(nearest);
            current = nearest;
        }
        return minutes;
    }

    private double distance(DayCheckPlace a, DayCheckPlace b) {
        return Geo.distanceMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    private double distance(double[] a, double[] b) {
        return Geo.distanceMeters(a[0], a[1], b[0], b[1]);
    }
}
