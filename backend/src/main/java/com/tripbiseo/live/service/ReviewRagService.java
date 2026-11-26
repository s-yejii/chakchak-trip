package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodCompareRequest;
import com.tripbiseo.live.dto.MoodCompareResponse;
import com.tripbiseo.live.dto.MoodMatch;

/**
 * ④ 리뷰 RAG: 리뷰를 임베딩(글의 뜻을 숫자 배열로 바꾼 것)해 두고, 분위기 질문에 뜻이 가까운 리뷰를 찾아요.
 * 찾은 리뷰를 근거로 Gemini가 맞는 가게를 고르게 해요 (RAG = 찾아서 근거로 넣고 답 만들기)
 */
public interface ReviewRagService {

    /**
     * 리뷰 RAG 관리 계정인지 확인. 아니면 403.
     * 계정 목록은 환경변수 RAG_ADMIN_ACCOUNTS (기본 ragadmin). 임베딩·평가 API가 Gemini 한도를 써서 막아 둬요
     */
    void checkAdmin(long memberId);

    /** 아직 임베딩이 없는 리뷰를 최대 limit개 임베딩해서 저장. 범위는 시연 동선 장소 500m 안 (개발용, 다 될 때까지 여러 번 부름) */
    EmbedResult embedReviews(int limit);

    /** 후보 가게들을 분위기(mood)에 맞는 순으로 줄 세움 + 근거 리뷰. 분위기와 먼 가게는 뺌 */
    List<MoodMatch> rankByMood(List<String> placeIds, String mood);

    /**
     * 분위기 질문: 후보 가게 중 분위기에 맞는 곳만, 고른 이유와 근거 리뷰를 붙여 돌려줌.
     *   1. 벡터로 뜻이 가까운 리뷰 찾기
     *   2. Gemini가 그 리뷰를 읽고 판단
     *   3. 인용문이 실제 리뷰에 있는지 검사
     * 후보에 임베딩이 없으면 예전 방식: 리뷰 2개 이상에 moodWord 글자가 있는 곳
     */
    List<MoodMatch> findByMood(List<String> placeIds, String mood, String moodWord);

    /**
     * 평가용: 같은 고정 후보(이 점 500m 안, 그 종류)에서 세 방식이 고른 상위 5곳과 걸린 시간.
     * A = 리뷰 글자 찾기, B = 벡터(임베딩 거리)만, C = 리뷰 RAG
     */
    MoodCompareResponse compareMood(MoodCompareRequest request);
}
