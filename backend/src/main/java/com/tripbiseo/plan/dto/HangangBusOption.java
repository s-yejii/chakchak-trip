package com.tripbiseo.plan.dto;

import lombok.Data;

/**
 * GET /api/plans/hangang-bus 응답: 예약까지 시간이 많이 뜰 때 "한강버스로도 가요" 한 줄 (11/16 ③).
 * 시각은 HH:mm, 시간은 분. 이동 수단은 walk(걷기) / transit(버스·지하철)
 */
@Data
public class HangangBusOption {
    private String boardPier;        // 탈 선착장 이름 (옥수)
    private String alightPier;       // 내릴 선착장 이름 (잠실)
    private String boardAt;          // 배 출발 13:13
    private String alightAt;         // 배 도착 14:03
    private String toBoardMode;      // 지금 위치 → 탈 선착장 수단
    private int toBoardMin;          // 그 이동 분
    private String afterAlightMode;  // 내린 선착장 → 목적지 수단
    private int afterAlightMin;      // 그 이동 분
    private String busNo;            // 내려서 탈 버스 번호 (버스가 없으면 null)
    private String afterAlightDetail; // 내려서 탈 노선 설명 (길찾기 그대로, 없으면 null)
    private String doorArriveAt;     // 목적지 도착 14:28
    private String arriveBy;         // 예약 시각 15:00
    private int slackMin;            // 예약 전 여유 분
    private String directArriveAt;   // 대중교통으로 바로 가면 도착 시각
    private int directWaitMin;       // 바로 가면 예약 전 기다리는 분
    private int restMin;             // 내린 선착장 근처에서 쉬었다 가도 되는 분 (예약 15분 전 도착 목표, 5분 단위, 0~30)
    private String leaveAt;          // 쉬고 나서 선착장을 떠나는 시각 (restMin이 0이면 배 도착 시각)
    private String relaxedArriveAt;  // 쉬고 가면 목적지 도착 시각
}
