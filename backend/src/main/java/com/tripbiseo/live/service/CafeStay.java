package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.trip.dto.Stop;

/**
 * 여행 중 다음 예약(고정 칸) 앞에 들르는 카페 머무는 시간 (11/17 ③ 주형 결정, ④ 예지 OK).
 * AI 카드("N분 쉬었다 가면 돼요"), [일정에 넣기]·비슷한 가게 담기·넣기 미리보기가 모두 이 계산을 같이 써요.
 *   머묾 = 예약 5분 전 도착에 맞춰 남는 시간 (= 예약 시각 − 5분 − 카페→예약지 이동 − 카페 도착 시각), 최대 60분
 *   15분도 안 되면 권하지 않음 ("들르면 빠듯해요"). 다음 예약이 없으면 기본 30분
 *   계산은 "30분 머물렀을 때 남는 여유"(slackAfterBase)로: 머묾 = 30 + 여유 − 5
 *   예) 30분 머물고 여유 13분 → 38분 · 여유 −1분 → 24분 · 여유 2시간 → 60분 · 여유 −12분 → 13분이라 안 권함
 */
final class CafeStay {

    static final int BASE_MIN = 30;       // 다음 예약이 없을 때 머묾, 그리고 여유를 잴 때 기준
    static final int MIN_MIN = 15;        // 이보다 짧게밖에 못 있으면 권하지 않음
    static final int MAX_MIN = 60;        // 여유가 많아도 이보다 오래는 안 머묾
    static final int KEEP_SLACK_MIN = 5;  // 다음 예약에 이만큼 먼저 도착

    private CafeStay() {}

    /** 카페·디저트·베이커리인지 (분류 글자로) */
    static boolean isCafe(String category) {
        return category != null && (category.contains("카페") || category.contains("디저트") || category.contains("베이커리"));
    }

    /** 머무는 분. 다음 예약이 없으면(null) 30분. 15분도 못 있으면 null (권하지 않음) */
    static Integer stayMinutes(Integer slackAfterBase) {
        if (slackAfterBase == null) {
            return BASE_MIN;
        }
        int stay = BASE_MIN + slackAfterBase - KEEP_SLACK_MIN;
        if (stay < MIN_MIN) {
            return null;
        }
        return Math.min(MAX_MIN, stay);
    }

    /**
     * 새로 넣는 카페 칸(id 없음)이 다음 예약(fixedStop) 앞에 있으면 머무는 시간을 예약에 맞춤.
     * slackMin = 지금 머무는 시간으로 계산한 예약 여유. 바꿨으면 true (다시 계산해야 함)
     */
    static boolean fitNewCafe(List<Stop> plan, Stop fixedStop, int slackMin) {
        Stop newStop = null;
        for (Stop stop : plan) {
            if (stop.getId() == null && !Boolean.TRUE.equals(stop.getFixed()) && isCafe(stop.getCategory())) {
                newStop = stop;
            }
        }
        if (newStop == null || fixedStop == null || newStop.getStay() == null || plan.indexOf(newStop) > plan.indexOf(fixedStop)) {
            return false;
        }
        Integer stay = stayMinutes(slackMin + newStop.getStay() - BASE_MIN);
        if (stay == null || stay.equals(newStop.getStay())) {
            return false;
        }
        newStop.setStay(stay);
        return true;
    }

    /**
     * 예약(fixedStop) 바로 앞 칸이 카페이고 여유가 KEEP_SLACK_MIN(5분) 이상이면 true:
     * 카페 머묾을 예약에 맞춰 넣은 계획대로라 "여유가 얼마 없어요" 경고를 안 띄움 (그 밖의 칸·계정은 경고 기준 그대로)
     */
    static boolean isFittedBefore(List<Stop> todayStops, Stop fixedStop, Integer slack) {
        if (todayStops == null || fixedStop == null || slack == null || slack < KEEP_SLACK_MIN) {
            return false;
        }
        int index = todayStops.indexOf(fixedStop);
        return index > 0 && isCafe(todayStops.get(index - 1).getCategory());
    }
}
