package com.tripbiseo.plan.dto;

import lombok.Data;

/** 한강버스 시간표 한 줄 (hangang_bus_time, ① 상재 db/24, 읽기만). route·direction·runNo가 같으면 같은 배 */
@Data
public class HangangBusTime {
    private String pierId;     // hangang-oksu
    private String route;      // 동부 / 서부
    private String direction;  // 잠실행 / 여의도행 / 마곡행
    private int runNo;         // 시간표의 몇 번째 줄
    private int timeMin;       // 자정부터 분 (13:13 → 793)
    private boolean arrival;   // 종점 도착 시각이면 true, 나머지는 출발 시각
}
