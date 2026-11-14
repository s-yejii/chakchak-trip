package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.live.dto.MoodReview;
import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;

/**
 * ④ 리뷰 RAG: 리뷰 임베딩 (review_embedding). SQL은 resources/mapper/live/ReviewEmbeddingMapper.xml
 * 시연 동선 장소 500m 안 리뷰만 임베딩해요 (약 3,100개, 무료 한도 때문에 11/15에 줄임)
 */
@Mapper
public interface ReviewEmbeddingMapper {

    /** 아직 임베딩이 없는 시연 동선 리뷰를 count개 (리뷰 id 순) */
    List<ReviewText> selectReviewToEmbedList(@Param("count") int count);

    /** 아직 임베딩이 없는 시연 동선 리뷰 수 */
    int selectRemainingCount();

    /** 임베딩 여러 개를 INSERT 한 문장으로 저장 (이미 있으면 건너뜀) */
    void insertReviewEmbeddingList(@Param("list") List<ReviewVector> list, @Param("model") String model);

    /** 후보 가게들의 리뷰 중 질문(query 임베딩)과 가까운 리뷰를 가게마다 perPlace개 (가게별 가까운 순) */
    List<MoodReview> selectMoodReviewList(@Param("query") String query, @Param("placeIds") List<String> placeIds,
                                          @Param("perPlace") int perPlace);
}
