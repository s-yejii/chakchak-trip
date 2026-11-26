package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.PlaceAskResult;

/**
 * ④ 여행 중 AI에게 묻기: 사용자 말로 장소를 찾아요.
 * Text-to-SQL: Gemini가 사용자 말을 SQL로 바꿔 주고, 서버가 검사한 뒤 실행하는 방식
 */
public interface PlaceAskService {

    /**
     * 사용자 말로 장소 찾기.
     * prevPlaceIds가 있으면 이어지는 질문이라 그 장소들 안에서만 찾아요 (없으면 null).
     * AI로 못 찾으면 fallback = true → 부르는 쪽이 반경을 골라 찾는 방식으로 넘어가요
     */
    PlaceAskResult findPlaces(long memberId, long tripId, String message, String prevMessage, List<String> prevPlaceIds,
                              double lat, double lng);
}
