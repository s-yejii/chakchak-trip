package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodMatch;

/** ④ 리뷰 RAG: 리뷰를 임베딩해 두고, 분위기 질문에 뜻이 가까운 리뷰를 찾아요 */
public interface ReviewRagService {

    /** 아직 임베딩이 없는 시연 동선(장소 500m 안) 리뷰를 최대 limit개 임베딩해서 저장 (개발용, 다 될 때까지 여러 번 부름) */
    EmbedResult embedReviews(int limit);

    /** 후보 가게들을 분위기(mood)에 맞는 순으로 줄 세움 + 근거 리뷰. 분위기와 먼 가게는 뺌 */
    List<MoodMatch> rankByMood(List<String> placeIds, String mood);
}
