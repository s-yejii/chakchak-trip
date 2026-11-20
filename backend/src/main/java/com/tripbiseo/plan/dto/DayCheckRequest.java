package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** ③ 하루에 다 갈 수 있는지 묻기 (11/20) */
@Data
public class DayCheckRequest {
    private List<String> placeIds = new ArrayList<>(); // 담은 장소 id (retrip_place.id)
    private int dayMinutes;                            // 하루 활동 시간(분)
}
