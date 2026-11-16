package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.MoodJudgeAnswer;
import com.tripbiseo.live.dto.MoodMatch;
import com.tripbiseo.live.dto.MoodReview;
import com.tripbiseo.live.mapper.ReviewEmbeddingMapper;
import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.service.MemberService;

/** ④ 리뷰 RAG: 근거 검사·대체 경로·관리 계정 (DB·Gemini는 가짜 객체로) */
class ReviewRagServiceImplTest {

    private static final List<String> CANDIDATES = List.of("a", "b");

    private ReviewEmbeddingMapper mapper;
    private AiService aiService;
    private MemberService memberService;
    private ReviewRagServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReviewEmbeddingMapper.class);
        ReviewEmbedder embedder = mock(ReviewEmbedder.class);
        aiService = mock(AiService.class);
        memberService = mock(MemberService.class);
        service = new ReviewRagServiceImpl(mapper, embedder, new MoodPromptMaker(), aiService, memberService);
        ReflectionTestUtils.setField(service, "adminAccounts", "ragadmin"); // @Value 대신 직접 넣음

        when(embedder.getModel()).thenReturn("test-model");
        when(embedder.embedQuery(anyString())).thenReturn(new float[] {0.1f, 0.2f});
        when(aiService.isReady()).thenReturn(true);
        when(mapper.selectEmbeddedPlaceCount(any(), anyString())).thenReturn(2);
        // a: 조용한 근거가 있는 가게(거리 0.20·0.30), b: 근거가 약한 가게(거리 0.25·0.35)
        when(mapper.selectMoodReviewList(anyString(), any(), anyInt(), anyInt(), anyString())).thenReturn(List.of(
                review("a", "평일 오후라 손님이 없어서 조용하게 작업했어요", 0.20),
                review("a", "음악 소리도 작아서 대화하기 좋았습니다", 0.30),
                review("b", "분위기가 좋고 커피가 맛있어요 또 올게요", 0.25),
                review("b", "사장님이 친절하시고 디저트가 맛있어요", 0.35)));
    }

    @Test
    void 가게마다_거리_평균으로_가까운_순으로_줄_세움() {
        List<MoodMatch> ranked = service.rankByMood(CANDIDATES, "조용한");

        assertEquals(List.of("a", "b"), placeIds(ranked));
        assertEquals(0.25, ranked.get(0).getScore(), 0.0001); // (0.20 + 0.30) / 2
        assertEquals(2, ranked.get(0).getReviews().size());
    }

    @Test
    void 인용문이_실제_리뷰에_없으면_그_가게는_뺌() {
        MoodJudgeAnswer answer = new MoodJudgeAnswer();
        answer.getPicks().add(pick(1, "손님이 없어 조용함", "손님이 없어서 조용하게"));  // a: 리뷰에 있는 글
        answer.getPicks().add(pick(2, "조용함", "정말 조용하고 한적해요"));             // b: 지어낸 글
        when(aiService.askJsonQuick(anyString(), eq(MoodJudgeAnswer.class))).thenReturn(answer);

        List<MoodMatch> found = service.findByMood(CANDIDATES, "조용한", "조용");

        assertEquals(List.of("a"), placeIds(found));
        assertEquals("손님이 없어서 조용하게", found.get(0).getQuote());
    }

    @Test
    void 임베딩_표가_없는_DB면_예전_글자_방식으로() {
        when(mapper.selectEmbeddedPlaceCount(any(), anyString()))
                .thenThrow(new InvalidDataAccessResourceUsageException("relation review_embedding does not exist"));
        when(mapper.selectKeywordPlaceIdList(any(), eq("조용"), anyInt())).thenReturn(List.of("b"));

        List<MoodMatch> found = service.findByMood(CANDIDATES, "조용한", "조용");

        assertEquals(List.of("b"), placeIds(found));
        assertNull(found.get(0).getQuote()); // 예전 방식은 근거 문장이 없음
    }

    @Test
    void Gemini_판단이_실패하면_벡터_순위를_그대로() {
        when(aiService.askJsonQuick(anyString(), eq(MoodJudgeAnswer.class))).thenThrow(new ApiException(502, "Gemini 오류"));

        List<MoodMatch> found = service.findByMood(CANDIDATES, "조용한", "조용");

        assertEquals(List.of("a", "b"), placeIds(found));
    }

    @Test
    void 관리_계정이_아니면_임베딩_평가_API를_못_씀() {
        when(memberService.getMember(anyLong())).thenReturn(member("tripuser"));
        ApiException error = assertThrows(ApiException.class, () -> service.checkAdmin(1L));
        assertEquals(403, error.getStatus());

        when(memberService.getMember(anyLong())).thenReturn(member("ragadmin"));
        assertDoesNotThrow(() -> service.checkAdmin(2L));
    }

    // ---------------------------------------------------------------- 도우미

    private MoodReview review(String placeId, String content, double distance) {
        MoodReview review = new MoodReview();
        review.setPlaceId(placeId);
        review.setReviewId(placeId + content.length());
        review.setContent(content);
        review.setDistance(distance);
        return review;
    }

    private MoodJudgeAnswer.Pick pick(int number, String reason, String quote) {
        MoodJudgeAnswer.Pick pick = new MoodJudgeAnswer.Pick();
        pick.setNumber(number);
        pick.setReason(reason);
        pick.setQuote(quote);
        return pick;
    }

    private Member member(String username) {
        Member member = new Member();
        member.setUsername(username);
        return member;
    }

    private List<String> placeIds(List<MoodMatch> matches) {
        List<String> ids = new ArrayList<>();
        for (MoodMatch match : matches) {
            ids.add(match.getPlaceId());
        }
        return ids;
    }
}
