package com.tripbiseo.plan.dto;

import lombok.Data;

/** 일정 칸 하나에 붙는 안내 (11/18 사용성): 점검 화면이 그 칸 옆에 보여 줘요 */
@Data
public class StopNote {
    private Integer day;   // 몇째 날 (1부터)
    private String time;   // 그 칸 시각 'HH:mm' (stops[]의 day·time과 짝)
    private String text;   // 한 줄 안내
    private String level;  // warn(노랑: 손보면 좋음) / info(회색: 알아만 두기)
}
