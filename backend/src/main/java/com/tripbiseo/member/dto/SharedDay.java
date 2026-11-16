package com.tripbiseo.member.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 공유 일정의 하루 */
@Data
public class SharedDay {
    private int day; // 몇째 날 (1부터)
    private List<SharedStop> stops = new ArrayList<>();
}
