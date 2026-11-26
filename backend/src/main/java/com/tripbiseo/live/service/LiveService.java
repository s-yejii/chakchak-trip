package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.live.dto.SkipRequest;

/**
 * ④ 여행 중: 채팅, 근처 추천, 일정에 넣기, 못 가게 된 일정 빼기.
 * 다른 일은 따로 있어요: 지금 상황은 StatusService, GPS 저장·도착 판정은 TrackService, 대안은 AlternativeService.
 * 모든 메서드 공통 오류 (검사하는 순서대로):
 *   1. 400 "내 위치를 먼저 찾아 주세요."
 *   2. 404 "여행을 찾을 수 없어요."
 *   3. 400 "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요."
 */
public interface LiveService {

    /** 여행 중 채팅: 말을 분류(추천 / 일정 바꾸기 / 질문)해서 답함. 낱말 규칙 먼저, 애매할 때만 Gemini */
    AssistResponse assist(long memberId, long tripId, AssistRequest request);

    /**
     * 반경 안 추천 3곳. 점수(보정 평점 + 원하는 리뷰 태그) 높은 순.
     * 가는 길 모드(onRoute: 놓친 가게 대신 다음 고정 일정 가는 길에서 찾기)면 5곳
     */
    RecommendResponse recommend(long memberId, long tripId, RecommendRequest request);

    /** 추천 장소를 일정에 넣고(기본은 바로 다음) 남은 시각 다시 계산 */
    InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request);

    /** 못 가게 된 일정 하나를 빼고, 남은 일정을 앞으로 당겨 다시 계산 (놓친 예약) */
    InsertResponse skipStop(long memberId, long tripId, SkipRequest request);
}
