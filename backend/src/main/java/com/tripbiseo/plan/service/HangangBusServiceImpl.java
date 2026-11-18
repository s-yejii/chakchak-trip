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
    private static final int ALIGHT_PIER_COUNT = 1; // 목적지에서 가장 가까운 선착장까지 배로 (중간에 내려 지하철로 갈아타는 안은 안 권함)
    private static final int WALK_MAX_MIN = 15;    // 걸어서 이 안이면 걷고, 넘으면 버스·지하철
    private static final int GUESS_EXTRA_MIN = 10; // 내린 선착장 → 목적지를 거리로 어림했으면 이만큼 더 (선착장이 물가라 어림이 실제보다 짧게 나와서, 11/16)
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
            afterAlightMin.put(pier.getId(), minutesOf(route));
        }
        Integer rainStart = liveWeatherChecker.findRainStart(fromLat, fromLng);
        int directArrive = nowMin + direct.getMinutes();
        HangangBusPicker.Choice choice = HangangBusPicker.pick(hangangBusMapper.selectTimeList(), readyAt, afterAlightMin, arriveByMin, directArrive, rainStart);
        if (choice == null || !isTowardDestination(fromLat, fromLng, findPier(boardPiers, choice.board.getPierId()), findPier(alightPiers, choice.alight.getPierId()), toLat, toLng)) {
            return null; // 11/17: 배가 목적지 쪽으로 가는 경우만 (잠실 스시이세이 → 동서울에 "뚝섬까지 가서 배로 잠실로 돌아오기"가 나왔어요)
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
        fillRest(option, choice, arriveByMin);
        return option;
    }

    // 걸어서 15분 안이면 걷기, 아니면 버스·지하철 (길찾기 결과)
    private Route findRoute(double lat1, double lng1, double lat2, double lng2) {
        Route walkGuess = routeClient.estimate(lat1, lng1, lat2, lng2, "walk"); // API 없이 어림
        String mode = walkGuess.getMinutes() <= WALK_MAX_MIN ? "walk" : "transit";
        return routeClient.getRoute(lat1, lng1, lat2, lng2, mode, TRANSPORTS, true);
    }

    // 여유가 많으면 내린 선착장 근처(한강공원)에서 쉬었다 가기: 예약 15분 전 도착 목표
    private void fillRest(HangangBusOption option, HangangBusPicker.Choice choice, int arriveByMin) {
        int restMin = HangangBusPicker.restMinutes(arriveByMin, choice.doorArrive);
        option.setRestMin(restMin);
        option.setLeaveAt(Geo.toHhmm(choice.alight.getTimeMin() + restMin));
        option.setRelaxedArriveAt(Geo.toHhmm(choice.doorArrive + restMin));
    }

    private void fillMoves(HangangBusOption option, Route toBoard, Route afterAlight) {
        option.setToBoardMode(toBoard.getMode());
        option.setToBoardMin(toBoard.getMinutes());
        option.setAfterAlightMode(afterAlight.getMode());
        option.setAfterAlightMin(minutesOf(afterAlight));
        option.setAfterAlightDetail(afterAlight.getDetail());
        option.setBusNo(findBusNo(afterAlight.getDetail()));
    }

    /** 내린 선착장 → 목적지 이동 분: 거리로 어림한 버스·지하철이면 10분 더 (Route는 길찾기 기억 칸과 같은 것이라 고치지 않고 숫자만 새로 냄) */
    static int minutesOf(Route route) {
        boolean isGuessedTransit = "estimate".equals(route.getSource()) && "transit".equals(route.getMode());
        return isGuessedTransit ? route.getMinutes() + GUESS_EXTRA_MIN : route.getMinutes();
    }

    /** 노선 설명 "버스 3317 잠일고등학교→잠실역 6정거장 · …"에서 첫 버스 번호. 버스가 없으면 null */
    static String findBusNo(String detail) {
        if (detail == null) {
            return null;
        }
        for (String ride : detail.split(" · ")) {
            String[] words = ride.split(" ");
            if (words.length > 1 && words[0].equals("버스")) {
                String busNo = words[1];
                return busNo.contains(":") ? busNo.substring(busNo.indexOf(':') + 1) : busNo; // TMAP은 "지선:3317"처럼 버스 종류를 앞에 붙여요 → 번호만 (11/17)
            }
        }
        return null;
    }

    /**
     * 배가 목적지 쪽으로 데려다주는지 (11/17): 탈 선착장이 목적지보다 가깝고(지나쳐 가지 않음),
     * 내릴 선착장이 탈 선착장보다 목적지에 가까울 때만 true. 거리는 직선(m)
     */
    static boolean isTowardDestination(double fromLat, double fromLng, HangangBusPier board, HangangBusPier alight, double toLat, double toLng) {
        if (board == null || alight == null) {
            return false;
        }
        double fromToDestination = Geo.distanceMeters(fromLat, fromLng, toLat, toLng);
        double fromToBoard = Geo.distanceMeters(fromLat, fromLng, board.getLat(), board.getLng());
        double boardToDestination = Geo.distanceMeters(board.getLat(), board.getLng(), toLat, toLng);
        double alightToDestination = Geo.distanceMeters(alight.getLat(), alight.getLng(), toLat, toLng);
        return fromToBoard < fromToDestination && alightToDestination < boardToDestination;
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
