package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.live.dto.MoodComparePlace;
import com.tripbiseo.live.dto.MoodReview;
import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;

/**
 * ④ 리뷰 RAG: 리뷰 임베딩(글의 뜻을 숫자 배열로 바꾼 것) 표 review_embedding.
 * SQL은 resources/mapper/live/ReviewEmbeddingMapper.xml
 * 임베딩 범위: 시연 동선 장소 500m 안, 가게마다 긴 리뷰 6개만 (약 1,830개).
 * Gemini 무료 한도가 하루 약 1,000개라 범위를 줄였어요.
 */
@Mapper
public interface ReviewEmbeddingMapper {

    /**
     * 아직 임베딩이 없는 시연 동선 리뷰를 count개, 리뷰 id 순으로.
     * minLength자 이상인 리뷰 중 가게마다 긴 순 perPlace개 안에서만 골라요
     */
    List<ReviewText> selectReviewToEmbedList(@Param("count") int count, @Param("minLength") int minLength,
                                             @Param("perPlace") int perPlace);

    /** 아직 임베딩이 없는 시연 동선 리뷰 수 (위와 같은 범위) */
    int selectRemainingCount(@Param("minLength") int minLength, @Param("perPlace") int perPlace);

    /** 임베딩 여러 개를 INSERT 한 문장으로 저장 (이미 있으면 건너뜀) */
    void insertReviewEmbeddingList(@Param("list") List<ReviewVector> list, @Param("model") String model);

    /**
     * 후보 가게마다 질문(query 임베딩)과 가장 가까운 리뷰 perPlace개, 가게별 가까운 순.
     * minLength자 이상 리뷰만, 같은 model로 만든 임베딩끼리만 비교해요
     */
    List<MoodReview> selectMoodReviewList(@Param("query") String query, @Param("placeIds") List<String> placeIds,
                                          @Param("perPlace") int perPlace, @Param("minLength") int minLength,
                                          @Param("model") String model);

    /** 후보 가게 중 이 모델로 임베딩된 리뷰가 있는 가게 수 (0이면 리뷰 RAG를 못 씀 → 예전 방식) */
    int selectEmbeddedPlaceCount(@Param("placeIds") List<String> placeIds, @Param("model") String model);

    /** 평가용 후보: 이 점에서 meters 안, 이 종류, 보정 평점 순 가게 (AI SQL의 기본 정렬과 같음) */
    List<MoodComparePlace> selectCandidateList(@Param("lat") double lat, @Param("lng") double lng,
                                               @Param("kind") String kind, @Param("meters") int meters);

    /** 예전 방식(대비용·평가 비교용): 리뷰 minCount개 이상에 word 글자가 들어간 후보 가게 id */
    List<String> selectKeywordPlaceIdList(@Param("placeIds") List<String> placeIds, @Param("word") String word,
                                          @Param("minCount") int minCount);
}
