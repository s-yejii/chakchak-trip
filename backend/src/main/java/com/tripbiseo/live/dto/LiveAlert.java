package com.tripbiseo.live.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 화면 맨 위 알림 1개 */
@Data
public class LiveAlert {
    // key 앞부분: lost- / overdue- / closed- / late- / leave- / missed- / tight- / rain-, 자동 조정 안내(adjust)는 adjust-
    private String key;     // 같은 알림을 두 번 띄우지 않으려는 키 (예: "tight-7")
    private String level;   // danger / warn
    private String title;
    private String text;
    @JsonInclude(JsonInclude.Include.NON_NULL) // leave- 알림에만 true, 그 밖에는 키 없음
    private Boolean repeat;
}
