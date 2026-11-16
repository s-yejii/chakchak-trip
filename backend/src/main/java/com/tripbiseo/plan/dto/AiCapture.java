package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini가 예약 캡처 사진에서 읽은 값 (JSON 답 모양). 안 보이면 null */
@Data
public class AiCapture {
    private String name;
    private String date;    // YYYY-MM-DD
    private String time;    // HH:mm
    private String address;
    private Boolean hasYear;    // 화면에 연도가 보였는지 (없으면 서버가 연도를 정함, 11/16)
    private String koreanName;  // 가게 이름이 영어면 한국에서 쓰는 한글 이름 (DB 찾기용, 11/16)
}
