package com.tripbiseo.live.dto;

import lombok.Data;

/** AI에게 묻기로 찾은 장소 한 곳 (화면 카드·지도 핀) */
@Data
public class AskedPlace {
    private String id;
    private String name;
    private String kind;           // cafe / food / bar …
    private String category;       // 세부 종류 (예: 카페,디저트)
    private Double lat;
    private Double lng;
    private Double rating;         // 보정 평점: 리뷰가 적으면 평균 4.6 쪽으로 당긴 평점 (예: 4.7)
    private Integer reviewCount;
    private String useTime;        // 영업시간 원문 (영업 중인지는 서버가 봄)
    private String restDate;       // 휴무 원문
    private Integer detourMinutes; // 가는 길에서 들르면 더 걸리는 분 (예: 4). 가는 길이 없으면 null
    private Double joinLat;        // 가는 길 위에서 이 장소와 가장 가까운 점 = 지도 점선 시작
    private Double joinLng;
    private String closeAt;        // 닫는 시각 HH:mm (예: "21:30"). 영업시간을 모르거나 24시간이면 null
    private Boolean closingSoon;   // 곧 닫음
    private Integer walkMinutes;   // 지금 위치 → 이곳 실제 걷는 시간(분). 다시 재지 않았으면 null
    private Boolean isEstimate;    // 다시 잴 때 길찾기가 실패해 거리로 어림했으면 true
    private Integer slackAfter;    // 들러서 stayMinutes만큼 머물면 다음 일정까지 남는 여유(분, 예: 6). 계산할 수 없으면 null
    private Integer stayMinutes;   // 들르면 머무는 분 (기본 30). 카페는 여유에 따라 15~60, 15분도 못 있으면 null (CafeStay)
    private String moodReason;     // 분위기로 고른 이유 한 줄 (리뷰 RAG, Gemini가 씀). 분위기 질문이 아니거나 못 쓰면 null
    private String moodEvidence;   // 근거 리뷰 문장 (실제 리뷰에 있는 글인지 서버가 확인함). 없으면 null
}
