package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;

/**
 * ④ 리뷰 RAG: 리뷰 임베딩 (review_embedding). SQL은 resources/mapper/live/ReviewEmbeddingMapper.xml
 * 시나리오 5개 구(중구·종로구·강남구·용산구·영등포구) 리뷰만 임베딩해요 (약 3.2만 개)
 */
@Mapper
public interface ReviewEmbeddingMapper {

    /** 아직 임베딩이 없는 5개 구 리뷰를 count개 (리뷰 id 순) */
    List<ReviewText> selectReviewToEmbedList(@Param("count") int count);

    /** 아직 임베딩이 없는 5개 구 리뷰 수 */
    int selectRemainingCount();

    /** 임베딩 여러 개를 INSERT 한 문장으로 저장 (이미 있으면 건너뜀) */
    void insertReviewEmbeddingList(@Param("list") List<ReviewVector> list, @Param("model") String model);
}
