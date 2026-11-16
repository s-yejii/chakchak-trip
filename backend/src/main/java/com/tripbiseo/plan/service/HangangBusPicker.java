package com.tripbiseo.plan.service;

import java.util.List;
import java.util.Map;

import com.tripbiseo.plan.dto.HangangBusTime;

/**
 * ③ 한강버스 추천의 규칙 (11/16). DB·길찾기 없이 숫자만 받아서 "탈 배 하나"를 고름 → 단위 테스트가 쉬움.
 * 규칙 (모두 맞아야 보여 줌):
 *  ① 탈 선착장에 배 떠나기 5분 전까지 갈 수 있는 배가 시간표에 있음
 *  ② 내려서 목적지까지 가면 예약 10분 전까지 도착
 *  ③ 대중교통으로 바로 가면 예약 전에 30분 넘게 기다림 (시간이 뜰 때만 권함)
 *  ④ 배에서 내리기 전에 비가 시작하지 않음 (비 예보가 없으면 막지 않음)
 * 후보가 여러 개면 목적지에 가장 빨리 도착하는 배
 */
public final class HangangBusPicker {

    static final int BOARD_EARLY_MIN = 5;   // 배 떠나기 이만큼 전에 선착장 도착
    static final int ARRIVE_EARLY_MIN = 10; // 예약 이만큼 전에 목적지 도착
    static final int DIRECT_WAIT_MIN = 30;  // 바로 가면 이만큼 넘게 기다릴 때만 권함
    static final int RELAXED_EARLY_MIN = 15; // 여유롭게 가면 예약 이만큼 전에 도착
    static final int REST_STEP_MIN = 5;     // 쉬는 시간은 5분 단위
    static final int REST_MAX_MIN = 30;     // 쉬는 시간은 최대 30분

    private HangangBusPicker() {}

    /** 고른 배: 탈 줄 · 내릴 줄 · 목적지 도착(분) */
    public static class Choice {
        public final HangangBusTime board;
        public final HangangBusTime alight;
        public final int doorArrive;

        Choice(HangangBusTime board, HangangBusTime alight, int doorArrive) {
            this.board = board;
            this.alight = alight;
            this.doorArrive = doorArrive;
        }
    }

    /**
     * 탈 배 하나. 없거나 규칙에 안 맞으면 null.
     * readyAt: 탈 선착장 id → 그 선착장에 도착하는 시각(분) / afterAlight: 내릴 선착장 id → 내려서 목적지까지 분
     * directArrive: 대중교통으로 바로 가면 도착 시각(분) / rainStart: 비 시작 시각(분), 없으면 null
     */
    public static Choice pick(List<HangangBusTime> times, Map<String, Integer> readyAt, Map<String, Integer> afterAlight,
            int arriveBy, int directArrive, Integer rainStart) {
        if (arriveBy - directArrive <= DIRECT_WAIT_MIN) {
            return null; // ③ 바로 가도 별로 안 기다림
        }
        Choice best = null;
        for (HangangBusTime board : times) {
            Integer ready = readyAt.get(board.getPierId());
            if (ready == null || board.isArrival() || board.getTimeMin() - BOARD_EARLY_MIN < ready) {
                continue; // ① 그 선착장이 아니거나 못 타는 배
            }
            Choice choice = findAlight(times, board, afterAlight, arriveBy, rainStart);
            if (choice != null && (best == null || choice.doorArrive < best.doorArrive)) {
                best = choice;
            }
        }
        return best;
    }

    // 같은 배(노선·방향·회차)로 내릴 수 있는 선착장 중 목적지에 가장 빨리 닿는 곳
    private static Choice findAlight(List<HangangBusTime> times, HangangBusTime board, Map<String, Integer> afterAlight,
            int arriveBy, Integer rainStart) {
        Choice best = null;
        for (HangangBusTime alight : times) {
            Integer after = afterAlight.get(alight.getPierId());
            if (after == null || !isSameBoat(board, alight) || alight.getTimeMin() <= board.getTimeMin()) {
                continue; // 다른 배이거나 탈 곳보다 앞 선착장
            }
            int doorArrive = alight.getTimeMin() + after;
            boolean isOnTime = doorArrive <= arriveBy - ARRIVE_EARLY_MIN;                    // ②
            boolean isDry = rainStart == null || rainStart > alight.getTimeMin();            // ④
            if (isOnTime && isDry && (best == null || doorArrive < best.doorArrive)) {
                best = new Choice(board, alight, doorArrive);
            }
        }
        return best;
    }

    /**
     * 내린 선착장 근처(한강공원)에서 쉬었다 가도 되는 분: 예약 15분 전 도착을 목표로 남는 시간.
     * 5분 단위로 내림, 0~30분. 예) 15:00 예약 · 바로 가면 14:28 도착 → 17분 → 15분
     */
    public static int restMinutes(int arriveBy, int doorArrive) {
        int spare = arriveBy - RELAXED_EARLY_MIN - doorArrive;
        if (spare < 0) {
            return 0;
        }
        return Math.min(REST_MAX_MIN, spare / REST_STEP_MIN * REST_STEP_MIN);
    }

    private static boolean isSameBoat(HangangBusTime a, HangangBusTime b) {
        return a.getRoute().equals(b.getRoute()) && a.getDirection().equals(b.getDirection()) && a.getRunNo() == b.getRunNo();
    }
}
