package com.tripbiseo.live.dto;

import lombok.Data;

/** 평가 결과의 가게 하나 (C만 reason·quote가 있음) */
@Data
public class MoodComparePlace {
    private String id;
    private String name;
    private String reason;
    private String quote;
}
