package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodCompareRequest;
import com.tripbiseo.live.dto.MoodComparePlace;
import com.tripbiseo.live.dto.MoodCompareResponse;
import com.tripbiseo.live.dto.MoodJudgeAnswer;
import com.tripbiseo.live.dto.MoodMatch;
import com.tripbiseo.live.dto.MoodReview;
import com.tripbiseo.live.dto.ReviewText;
import com.tripbiseo.live.dto.ReviewVector;
import com.tripbiseo.live.mapper.ReviewEmbeddingMapper;
import com.tripbiseo.member.service.MemberService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewRagServiceImpl implements ReviewRagService {

    private static final int MAX_LIMIT = 5000;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REVIEWS_PER_PLACE = 2;          // 가게마다 근거 리뷰 2개 ("리뷰 2개 이상에 나와야 인정" 규칙과 같은 수)
    private static final double MAX_MOOD_DISTANCE = 0.40;    // 거리가 이보다 멀면 분위기와 상관없는 가게로 봄 (0이면 뜻이 같음. 임시값, 7단계 평가로 정함)
    private static final int MIN_REVIEW_LENGTH = 20;         // 20자보다 짧은 리뷰("좋아요")는 임베딩도, 근거로도 안 씀. 짧은 글은 아무 질문에나 가깝게 나와서
    private static final int EMBED_PER_PLACE = 6;            // 임베딩은 가게마다 긴 리뷰 6개만 (무료 한도 때문에)

    private static final int JUDGE_COUNT = 8;                // Gemini에게 보여 줄 후보 수 (벡터 상위 8곳 × 리뷰 2개)
    private static final int KEYWORD_MIN_COUNT = 2;          // 예전 방식: 리뷰 2개 이상에 글자가 나와야 인정
    private static final int MIN_QUOTE_LENGTH = 5;           // 인용문이 이보다 짧으면 근거로 안 봄 ("좋아요" 한 마디)
    private static final int MAX_REASON_LENGTH = 40;
    private static final int COMPARE_RADIUS_M = 500;         // 평가 후보: 시연 장소에서 500m 안 (임베딩 범위와 같음)
    private static final int COMPARE_TOP = 5;                // 평가: 방식마다 상위 5곳 (Precision@5)

    private static final Logger log = LoggerFactory.getLogger(ReviewRagServiceImpl.class);

    private final ReviewEmbeddingMapper reviewEmbeddingMapper;
    private final ReviewEmbedder reviewEmbedder;
    private final MoodPromptMaker moodPromptMaker;
    private final AiService aiService;
    private final MemberService memberService;     // 관리 계정 확인 (① 회원 읽기만)

    // 리뷰 RAG 관리 계정 아이디. 여러 개면 쉼표로 (예: "ragadmin,admin2"). 기본 ragadmin은 db/20_review_rag.sql이 만들어요
    @Value("${RAG_ADMIN_ACCOUNTS:ragadmin}")
    private String adminAccounts;

    /** 로그인한 회원 아이디가 관리 계정 목록에 있는지. 없으면 403 */
    @Override
    public void checkAdmin(long memberId) {
        String username = memberService.getMember(memberId).getUsername();
        for (String account : adminAccounts.split(",")) {
            if (account.trim().equals(username)) {
                return;
            }
        }
        throw new ApiException(403, "리뷰 RAG 관리 계정만 쓸 수 있어요.");
    }

    /**
     * 리뷰를 100개씩 꺼내서 Gemini로 임베딩하고 저장해요.
     * 한도(429)에 걸리면 거기서 멈추고 된 만큼 알려 줘요.
     * @Transactional은 일부러 안 붙여요. 붙이면 한도에 걸릴 때 앞에서 저장한 것까지 전부 취소돼서, 된 만큼 남기려고요
     */
    @Override
    public EmbedResult embedReviews(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(400, "limit은 1~" + MAX_LIMIT + "이에요.");
        }
        int embedded = 0;
        while (embedded < limit) {
            int count = Math.min(ReviewEmbedder.MAX_BATCH, limit - embedded);
            List<ReviewText> reviews = reviewEmbeddingMapper.selectReviewToEmbedList(count, MIN_REVIEW_LENGTH, EMBED_PER_PLACE);
            if (reviews.isEmpty()) {
                break; // 다 했어요
            }
            if (!embedAndSave(reviews)) {
                break; // 한도에 걸림 → 된 만큼만
            }
            embedded += reviews.size();
        }
        return new EmbedResult(embedded, reviewEmbeddingMapper.selectRemainingCount(MIN_REVIEW_LENGTH, EMBED_PER_PLACE));
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
     * 분위기로 가게 줄 세우기.
     *   1. 질문을 임베딩
     *   2. 후보 가게마다 질문과 가장 가까운 리뷰 2개를 찾음
     *   3. 가게 점수 = 그 2개 거리의 평균. 작을수록 앞
     * 근거 리뷰가 2개가 안 되거나(한 리뷰만 우연히 비슷한 곳) 점수가 MAX_MOOD_DISTANCE보다 크면 빼요
     */
    @Override
    public List<MoodMatch> rankByMood(List<String> placeIds, String mood) {
        if (mood == null || mood.isBlank() || placeIds == null || placeIds.isEmpty()) {
            return new ArrayList<>();
        }
        String query = toVectorText(reviewEmbedder.embedQuery(mood));
        List<MoodReview> rows = reviewEmbeddingMapper.selectMoodReviewList(query, placeIds, REVIEWS_PER_PLACE, MIN_REVIEW_LENGTH,
                                                                          reviewEmbedder.getModel());
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

    /**
     * 분위기 질문 (리뷰 RAG).
     *   R 찾기: 벡터로 상위 8곳
     *   A·G 넣고 만들기: Gemini가 그 리뷰를 읽고 맞는 곳만 고름
     *   검사: 인용문이 실제 리뷰에 있는지
     * 임베딩이 없거나 실패하면 예전 방식(리뷰 글자), Gemini가 실패하면 벡터 순위 그대로
     */
    @Override
    public List<MoodMatch> findByMood(List<String> placeIds, String mood, String moodWord) {
        if (placeIds == null || placeIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<MoodMatch> ranked;
        try {
            if (reviewEmbeddingMapper.selectEmbeddedPlaceCount(placeIds, reviewEmbedder.getModel()) == 0) {
                log.info("리뷰 RAG: 후보에 임베딩이 없어 예전 방식(리뷰 글자 '{}')", moodWord);
                return findByKeyword(placeIds, moodWord);
            }
            ranked = rankByMood(placeIds, mood);
        } catch (ApiException e) { // 질문 임베딩 실패 (한도·키 없음)
            log.warn("리뷰 RAG: 질문 임베딩 실패 → 예전 방식 - {}", e.getMessage());
            return findByKeyword(placeIds, moodWord);
        } catch (DataAccessException e) { // review_embedding 표가 없는 DB (20_review_rag.sql을 안 돌린 PC) → 예전 방식
            log.warn("리뷰 RAG: DB에 임베딩 표가 없거나 오류 → 예전 방식 - {}", e.getMostSpecificCause().getMessage());
            return findByKeyword(placeIds, moodWord);
        }
        List<MoodMatch> top = ranked.size() > JUDGE_COUNT ? new ArrayList<>(ranked.subList(0, JUDGE_COUNT)) : ranked;
        return judgeWithGemini(top, mood);
    }

    /** 평가용: 같은 후보로 A·B·C를 차례로 돌리고 각각 걸린 시간을 잼 */
    @Override
    public MoodCompareResponse compareMood(MoodCompareRequest request) {
        List<MoodComparePlace> candidates = reviewEmbeddingMapper.selectCandidateList(
                request.getLat(), request.getLng(), request.getKind(), COMPARE_RADIUS_M);
        Map<String, String> names = new HashMap<>(); // 가게 id → 이름 (결과에 이름을 붙이려고)
        List<String> placeIds = new ArrayList<>();
        for (MoodComparePlace candidate : candidates) {
            names.put(candidate.getId(), candidate.getName());
            placeIds.add(candidate.getId());
        }
        MoodCompareResponse response = new MoodCompareResponse();
        response.setCandidateCount(placeIds.size());
        if (placeIds.isEmpty()) {
            return response;
        }
        response.setEmbeddedCount(reviewEmbeddingMapper.selectEmbeddedPlaceCount(placeIds, reviewEmbedder.getModel()));

        long start = System.currentTimeMillis();
        response.setKeyword(toComparePlaces(findByKeyword(placeIds, request.getMoodWord()), names));
        response.setKeywordMs(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        try {
            response.setVector(toComparePlaces(rankByMood(placeIds, request.getMood()), names));
        } catch (ApiException e) { // 질문 임베딩 실패(한도)면 B는 빈 결과로 두고 계속
            log.warn("평가: B(벡터) 실패 - {}", e.getMessage());
        }
        response.setVectorMs(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        response.setRag(toComparePlaces(findByMood(placeIds, request.getMood(), request.getMoodWord()), names));
        response.setRagMs(System.currentTimeMillis() - start);
        return response;
    }

    // 결과 앞 5곳을 이름 붙여서
    private List<MoodComparePlace> toComparePlaces(List<MoodMatch> matches, Map<String, String> names) {
        List<MoodComparePlace> places = new ArrayList<>();
        for (MoodMatch match : matches) {
            if (places.size() >= COMPARE_TOP) {
                break;
            }
            MoodComparePlace place = new MoodComparePlace();
            place.setId(match.getPlaceId());
            place.setName(names.get(match.getPlaceId()));
            place.setReason(match.getReason());
            place.setQuote(match.getQuote());
            places.add(place);
        }
        return places;
    }

    // 예전 방식: 리뷰 2개 이상에 moodWord 글자가 나온 후보 (후보 순서 그대로)
    private List<MoodMatch> findByKeyword(List<String> placeIds, String moodWord) {
        List<MoodMatch> matches = new ArrayList<>();
        if (moodWord == null || moodWord.isBlank()) {
            return matches;
        }
        List<String> foundIds = reviewEmbeddingMapper.selectKeywordPlaceIdList(placeIds, moodWord.trim(), KEYWORD_MIN_COUNT);
        for (String placeId : placeIds) {
            if (foundIds.contains(placeId)) {
                MoodMatch match = new MoodMatch();
                match.setPlaceId(placeId);
                matches.add(match);
            }
        }
        return matches;
    }

    // Gemini가 리뷰를 읽고 분위기에 맞는 곳만 고름. 실패하면 벡터 순위 그대로 (이유 없이)
    private List<MoodMatch> judgeWithGemini(List<MoodMatch> top, String mood) {
        if (top.isEmpty() || !aiService.isReady()) {
            return top;
        }
        MoodJudgeAnswer answer;
        try {
            answer = aiService.askJsonQuick(moodPromptMaker.makePrompt(mood, top), MoodJudgeAnswer.class);
        } catch (ApiException e) {
            log.warn("리뷰 RAG: Gemini 판단 실패 → 벡터 순위 그대로 - {}", e.getMessage());
            return top;
        }
        return keepVerifiedPicks(top, answer);
    }

    // Gemini가 고른 곳 중 인용문이 실제 리뷰에 있는 곳만 (지어낸 근거 막기). 순서는 벡터 순위
    private List<MoodMatch> keepVerifiedPicks(List<MoodMatch> top, MoodJudgeAnswer answer) {
        List<MoodMatch> picked = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            MoodJudgeAnswer.Pick pick = findPick(answer, i + 1);
            if (pick == null) {
                continue; // Gemini가 안 고름
            }
            MoodMatch match = top.get(i);
            if (!isQuoteInReviews(pick.getQuote(), match.getReviews())) {
                log.info("리뷰 RAG: 인용문이 리뷰에 없어 뺌 - {} / {}", match.getPlaceId(), pick.getQuote());
                continue;
            }
            match.setReason(cutText(pick.getReason(), MAX_REASON_LENGTH));
            match.setQuote(pick.getQuote().trim());
            picked.add(match);
        }
        return picked;
    }

    // 답에서 그 번호의 고른 것 (없으면 null)
    private MoodJudgeAnswer.Pick findPick(MoodJudgeAnswer answer, int number) {
        if (answer == null || answer.getPicks() == null) {
            return null;
        }
        for (MoodJudgeAnswer.Pick pick : answer.getPicks()) {
            if (pick.getNumber() != null && pick.getNumber() == number) {
                return pick;
            }
        }
        return null;
    }

    // 인용문이 리뷰 글 안에 그대로 있는지 (띄어쓰기는 무시하고 비교)
    private boolean isQuoteInReviews(String quote, List<String> reviews) {
        if (quote == null || removeSpaces(quote).length() < MIN_QUOTE_LENGTH) {
            return false;
        }
        String target = removeSpaces(quote);
        for (String review : reviews) {
            if (removeSpaces(review).contains(target)) {
                return true;
            }
        }
        return false;
    }

    private String removeSpaces(String text) {
        return text.replaceAll("\\s", ""); // \\s: 띄어쓰기·줄바꿈 같은 빈칸 글자 (정규식)
    }

    private String cutText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
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
