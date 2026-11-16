package com.tripbiseo.plan.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.service.LiveWeatherChecker;
import com.tripbiseo.plan.dto.HangangBusOption;
import com.tripbiseo.plan.dto.HangangBusPier;
import com.tripbiseo.plan.dto.HangangBusTime;
import com.tripbiseo.plan.mapper.HangangBusMapper;

import lombok.RequiredArgsConstructor;

/**
 * ③ 한강버스 추천 (11/16). 고르는 규칙은 HangangBusPicker, 여기서는 숫자를 모아 줌:
 *  선착장(가까운 곳 몇 개) → 길찾기(RouteClient: 걸어서 15분 안이면 걷기, 아니면 버스·지하철) → 시간표 → 비 예보(④ 읽기만)
 * 선착장·시간표는 ① 상재 표라 읽기만 해요 (db/24_hangang_bus.sql)
 */
@Service
@RequiredArgsConstructor
public class HangangBusServiceImpl implements HangangBusService {

    private static final int BOARD_NEAR_M = 6000;  // 탈 선착장: 지금 위치에서 이 안
    private static final int BOARD_PIER_COUNT = 3;
    private static final int ALIGHT_NEAR_M = 4000; // 내릴 선착장: 목적지에서 이 안
    private static final int ALIGHT_PIER_COUNT = 2;
    private static final int WALK_MAX_MIN = 15;    // 걸어서 이 안이면 걷고, 넘으면 버스·지하철
    private static final List<String> TRANSPORTS = List.of("subway", "bus", "walk");

    private final HangangBusMapper hangangBusMapper;
    private final RouteClient routeClient;               // 공통 길찾기 (그대로 씀)
    private final LiveWeatherChecker liveWeatherChecker; // ④ 오늘 비 시작 시각 (시연 비 포함, 읽기만)

    @Override
    public HangangBusOption getOption(double fromLat, double fromLng, double toLat, double toLng, String arriveBy, String now) {
        int nowMin = now == null ? Geo.nowMinutes() : Geo.toMinutes(now);
        int arriveByMin = Geo.toMinutes(arriveBy);
        List<HangangBusPier> boardPiers = hangangBusMapper.selectPierList(fromLat, fromLng, BOARD_NEAR_M, BOARD_PIER_COUNT);
        List<HangangBusPier> alightPiers = hangangBusMapper.selectPierList(toLat, toLng, ALIGHT_NEAR_M, ALIGHT_PIER_COUNT);
        if (boardPiers.isEmpty() || alightPiers.isEmpty()) {
            return null; // 한강 근처가 아님
        }
        Route direct = routeClient.getRoute(fromLat, fromLng, toLat, toLng, "transit", TRANSPORTS, true);
        Map<String, Route> toBoard = new HashMap<>();
        Map<String, Integer> readyAt = new HashMap<>();
        for (HangangBusPier pier : boardPiers) {
            Route route = findRoute(fromLat, fromLng, pier.getLat(), pier.getLng());
            toBoard.put(pier.getId(), route);
            readyAt.put(pier.getId(), nowMin + route.getMinutes());
        }
        Map<String, Route> afterAlight = new HashMap<>();
        Map<String, Integer> afterAlightMin = new HashMap<>();
        for (HangangBusPier pier : alightPiers) {
            Route route = findRoute(pier.getLat(), pier.getLng(), toLat, toLng);
            afterAlight.put(pier.getId(), route);
            afterAlightMin.put(pier.getId(), route.getMinutes());
        }
        Integer rainStart = liveWeatherChecker.findRainStart(fromLat, fromLng);
        int directArrive = nowMin + direct.getMinutes();
        HangangBusPicker.Choice choice = HangangBusPicker.pick(hangangBusMapper.selectTimeList(), readyAt, afterAlightMin, arriveByMin, directArrive, rainStart);
        if (choice == null) {
            return null;
        }
        HangangBusOption option = new HangangBusOption();
        option.setBoardPier(shortName(findPier(boardPiers, choice.board.getPierId())));
        option.setAlightPier(shortName(findPier(alightPiers, choice.alight.getPierId())));
        option.setBoardAt(Geo.toHhmm(choice.board.getTimeMin()));
        option.setAlightAt(Geo.toHhmm(choice.alight.getTimeMin()));
        fillMoves(option, toBoard.get(choice.board.getPierId()), afterAlight.get(choice.alight.getPierId()));
        option.setDoorArriveAt(Geo.toHhmm(choice.doorArrive));
        option.setArriveBy(arriveBy);
        option.setSlackMin(arriveByMin - choice.doorArrive);
        option.setDirectArriveAt(Geo.toHhmm(directArrive));
        option.setDirectWaitMin(arriveByMin - directArrive);
        return option;
    }

    // 걸어서 15분 안이면 걷기, 아니면 버스·지하철 (길찾기 결과)
    private Route findRoute(double lat1, double lng1, double lat2, double lng2) {
        Route walkGuess = routeClient.estimate(lat1, lng1, lat2, lng2, "walk"); // API 없이 어림
        String mode = walkGuess.getMinutes() <= WALK_MAX_MIN ? "walk" : "transit";
        return routeClient.getRoute(lat1, lng1, lat2, lng2, mode, TRANSPORTS, true);
    }

    private void fillMoves(HangangBusOption option, Route toBoard, Route afterAlight) {
        option.setToBoardMode(toBoard.getMode());
        option.setToBoardMin(toBoard.getMinutes());
        option.setAfterAlightMode(afterAlight.getMode());
        option.setAfterAlightMin(afterAlight.getMinutes());
        option.setAfterAlightDetail(afterAlight.getDetail());
        option.setBusNo(findBusNo(afterAlight.getDetail()));
    }

    /** 노선 설명 "버스 3317 잠일고등학교→잠실역 6정거장 · …"에서 첫 버스 번호. 버스가 없으면 null */
    static String findBusNo(String detail) {
        if (detail == null) {
            return null;
        }
        for (String ride : detail.split(" · ")) {
            String[] words = ride.split(" ");
            if (words.length > 1 && words[0].equals("버스")) {
                return words[1];
            }
        }
        return null;
    }

    private HangangBusPier findPier(List<HangangBusPier> piers, String id) {
        for (HangangBusPier pier : piers) {
            if (pier.getId().equals(id)) {
                return pier;
            }
        }
        return null;
    }

    // "한강버스 옥수선착장" → "옥수"
    private String shortName(HangangBusPier pier) {
        return pier.getName().replace("한강버스", "").replace("선착장", "").trim();
    }
}
