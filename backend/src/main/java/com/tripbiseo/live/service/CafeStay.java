package com.tripbiseo.live.service;

/**
 * 여행 중 들르는 카페 머무는 시간 (11/17 ③ 주형 결정, ④ 예지 OK).
 * AI 카드("들러도 여유"), [일정에 넣기]·비슷한 가게 담기, 링크로 넣기가 모두 이 규칙을 같이 써서
 * 카드에서 본 여유와 넣은 뒤 여유가 같아요.
 *   기본 30분. 30분 머물고도 다음 예약(고정 칸)까지 여유가 많으면 예약 15분 전 도착까지 더 머묾 (최대 60분)
 *   예) 여유 43분 → 30분(여유 13분) · 여유 2시간 → 60분(남는 시간은 여유) · 다음 예약 없음 → 30분
 */
final class CafeStay {

    static final int BASE_MIN = 30;       // 카페 기본 머묾
    static final int MAX_MIN = 60;        // 여유가 많아도 이보다 오래는 안 머묾
    static final int KEEP_SLACK_MIN = 15; // 늘려도 다음 예약 이만큼 전에는 도착

    private CafeStay() {}

    /** 카페·디저트·베이커리인지 (분류 글자로) */
    static boolean isCafe(String category) {
        return category != null && (category.contains("카페") || category.contains("디저트") || category.contains("베이커리"));
    }

    /** 30분 머물렀을 때 남는 여유(분)로 더 머물 분. 다음 예약이 없으면(null) 0 */
    static int extraMinutes(Integer slackAfterBase) {
        if (slackAfterBase == null || slackAfterBase <= KEEP_SLACK_MIN) {
            return 0;
        }
        return Math.min(MAX_MIN - BASE_MIN, slackAfterBase - KEEP_SLACK_MIN);
    }
}
