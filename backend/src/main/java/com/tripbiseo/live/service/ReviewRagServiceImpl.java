package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodMatch;
import com.tripbiseo.live.dto.MoodReview;
import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;
import com.tripbiseo.live.mapper.ReviewEmbeddingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewRagServiceImpl implements ReviewRagService {

    private static final int MAX_LIMIT = 5000;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REVIEWS_PER_PLACE = 2;          // 가게마다 근거 리뷰 2개 (지금 규칙 "리뷰 2개 이상에 나와야 인정"과 같음)
    private static final double MAX_MOOD_DISTANCE = 0.40;    // 이보다 멀면 분위기와 상관없는 가게로 봄 (임시값, 7단계 평가로 정함)

    private final ReviewEmbeddingMapper reviewEmbeddingMapper;
    private final ReviewEmbedder reviewEmbedder;

    /**
     * 100개씩: 꺼내기 → Gemini로 임베딩 → 저장. 한도(429)에 걸리면 거기서 멈추고 된 만큼 알려 줘요.
     * @Transactional을 일부러 안 붙여요: 붙이면 한도에 걸릴 때 앞에서 저장한 것까지 전부 취소돼요. 여기선 된 만큼 남기는 게 맞아요
     */
    @Override
    public EmbedResult embedReviews(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(400, "limit은 1~" + MAX_LIMIT + "이에요.");
        }
        int embedded = 0;
        while (embedded < limit) {
            int count = Math.min(ReviewEmbedder.MAX_BATCH, limit - embedded);
            List<ReviewText> reviews = reviewEmbeddingMapper.selectReviewToEmbedList(count);
            if (reviews.isEmpty()) {
                break; // 다 했어요
            }
            if (!embedAndSave(reviews)) {
                break; // 한도에 걸림 → 된 만큼만
            }
            embedded += reviews.size();
        }
        return new EmbedResult(embedded, reviewEmbeddingMapper.selectRemainingCount());
    }

    // 리뷰 묶음 하나를 임베딩해서 저장. 한도(429)에 걸리면 저장하지 않고 false (다른 오류는 그대로 던짐)
    private boolean embedAndSave(List<ReviewText> reviews) {
        List<String> texts = new ArrayList<>();
        for (ReviewText review : reviews) {
            texts.add(review.getContent());
        }
        List<float[]> vectors;
        try {
            vectors = reviewEmbedder.embedDocuments(texts);
        } catch (ApiException e) {
            if (e.getStatus() == TOO_MANY_REQUESTS) {
                return false;
            }
            throw e;
        }
        List<ReviewVector> rows = new ArrayList<>();
        for (int i = 0; i < reviews.size(); i++) {
            rows.add(new ReviewVector(reviews.get(i).getReviewId(), toVectorText(vectors.get(i))));
        }
        reviewEmbeddingMapper.insertReviewEmbeddingList(rows, reviewEmbedder.getModel());
        return true;
    }

    /**
     * 질문을 임베딩 → 후보 가게마다 가까운 리뷰 2개 → 가게 점수 = 2개 거리 평균 → 점수 좋은 순.
     * 근거 리뷰가 2개가 안 되거나(한 리뷰만 우연히 비슷한 곳) 점수가 MAX_MOOD_DISTANCE보다 나쁘면 뺌
     */
    @Override
    public List<MoodMatch> rankByMood(List<String> placeIds, String mood) {
        if (mood == null || mood.isBlank() || placeIds == null || placeIds.isEmpty()) {
            return new ArrayList<>();
        }
        String query = toVectorText(reviewEmbedder.embedQuery(mood));
        List<MoodReview> rows = reviewEmbeddingMapper.selectMoodReviewList(query, placeIds, REVIEWS_PER_PLACE);
        List<MoodMatch> matches = new ArrayList<>();
        for (MoodMatch match : groupByPlace(rows)) {
            if (match.getReviews().size() >= REVIEWS_PER_PLACE && match.getScore() <= MAX_MOOD_DISTANCE) {
                matches.add(match);
            }
        }
        // Comparator.comparingDouble: score 값을 꺼내서 작은 것부터 정렬하라는 규칙 (Collections 정렬과 같은 일)
        matches.sort(Comparator.comparingDouble(MoodMatch::getScore));
        return matches;
    }

    // 리뷰 줄들(가게별 가까운 순)을 가게 하나에 MoodMatch 하나로 모음. 점수 = 리뷰 거리 평균
    private List<MoodMatch> groupByPlace(List<MoodReview> rows) {
        Map<String, MoodMatch> byPlace = new LinkedHashMap<>(); // 가게 id → 모으는 중인 결과 (들어온 순서 유지)
        for (MoodReview row : rows) {
            MoodMatch match = byPlace.get(row.getPlaceId());
            if (match == null) {
                match = new MoodMatch();
                match.setPlaceId(row.getPlaceId());
                byPlace.put(row.getPlaceId(), match);
            }
            match.setScore(match.getScore() + row.getDistance()); // 일단 합계
            match.getReviews().add(row.getContent());
        }
        for (MoodMatch match : byPlace.values()) {
            match.setScore(match.getScore() / match.getReviews().size()); // 합계 → 평균
        }
        return new ArrayList<>(byPlace.values());
    }

    // float[] → pgvector가 읽는 글자 "[0.1,0.2,…]"
    private String toVectorText(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
