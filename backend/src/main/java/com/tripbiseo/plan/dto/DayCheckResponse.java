package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** ③ 하루에 다 갈 수 있는지 결과 (11/20) */
@Data
public class DayCheckResponse {
    private int groups;                                    // 동네(묶음) 수
    private List<List<String>> groupNames = new ArrayList<>(); // 묶음마다 장소 이름
    private int stayMin;  // 머무는 시간 합
    private int walkMin;  // 묶음 안 걷기
    private int moveMin;  // 묶음 사이 대중교통 어림
    private int needMin;  // 합계
    private int dayMin;   // 하루 활동 시간
    private boolean over; // 하루를 넘는지
}
