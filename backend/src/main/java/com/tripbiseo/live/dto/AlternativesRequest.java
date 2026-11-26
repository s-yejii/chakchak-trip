package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/alternatives 요청 */
@Data
public class AlternativesRequest {
    private Double lat;
    private Double lng;
    private String reason = "delay"; // 대안을 만드는 이유 (예: "delay", 채팅에서는 "request"). Gemini에게 그대로 전달
    private String request;          // 사용자 요청 (채팅에서 넘어올 때). 비 올 때 실내 대안은 만들지 않음
}
