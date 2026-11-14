package com.tripbiseo.live.service;

import com.tripbiseo.live.dto.EmbedResult;

/** ④ 리뷰 RAG: 리뷰를 임베딩해 두고, 분위기 질문에 뜻이 가까운 리뷰를 찾아요 */
public interface ReviewRagService {

    /** 아직 임베딩이 없는 시나리오 5개 구 리뷰를 최대 limit개 임베딩해서 저장 (개발용, 다 될 때까지 여러 번 부름) */
    EmbedResult embedReviews(int limit);
}
