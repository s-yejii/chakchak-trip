package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;
import com.tripbiseo.live.mapper.ReviewEmbeddingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewRagServiceImpl implements ReviewRagService {

    private static final int MAX_LIMIT = 5000;
    private static final int TOO_MANY_REQUESTS = 429;

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
