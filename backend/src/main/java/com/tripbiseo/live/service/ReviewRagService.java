package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodCompareRequest;
import com.tripbiseo.live.dto.MoodCompareResponse;
import com.tripbiseo.live.dto.MoodMatch;

/** ④ 리뷰 RAG: 리뷰를 임베딩해 두고, 분위기 질문에 뜻이 가까운 리뷰를 찾아요 */
public interface ReviewRagService {

    /** 아직 임베딩이 없는 시연 동선(장소 500m 안) 리뷰를 최대 limit개 임베딩해서 저장 (개발용, 다 될 때까지 여러 번 부름) */
    EmbedResult embedReviews(int limit);

    /** 후보 가게들을 분위기(mood)에 맞는 순으로 줄 세움 + 근거 리뷰. 분위기와 먼 가게는 뺌 */
    List<MoodMatch> rankByMood(List<String> placeIds, String mood);

    /**
     * 분위기 질문: 후보 가게 중 분위기에 맞는 곳만 이유·근거 리뷰와 함께 (벡터로 찾기 → Gemini 판단 → 인용 검사).
     * 후보에 임베딩이 없으면 예전 방식(리뷰 2개 이상에 moodWord 글자)
     */
    List<MoodMatch> findByMood(List<String> placeIds, String mood, String moodWord);

    /** 평가용: 고정 후보(이 점 500m 안 그 종류)에서 A(글자)·B(벡터)·C(RAG)가 고른 상위 5와 걸린 시간 */
    MoodCompareResponse compareMood(MoodCompareRequest request);
}
