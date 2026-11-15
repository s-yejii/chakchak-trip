package com.tripbiseo.live.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.live.dto.EmbedResult;
import com.tripbiseo.live.dto.MoodCompareRequest;
import com.tripbiseo.live.dto.MoodCompareResponse;
import com.tripbiseo.live.dto.MoodMatch;
import com.tripbiseo.live.dto.MoodRankRequest;
import com.tripbiseo.live.service.ReviewRagService;

import lombok.RequiredArgsConstructor;

/** ④ 리뷰 RAG (11/15). 여행 하나에 딸린 기능이 아니라서 LiveController(/api/trips/{id}/live)와 따로 */
@RestController
@RequestMapping("/api/live/rag")
@RequiredArgsConstructor
public class ReviewRagController {

    private final ReviewRagService reviewRagService;

    /** 리뷰 임베딩 채우기 (개발용, remaining이 0이 될 때까지 여러 번 부름) */
    @PostMapping("/embed")
    public EmbedResult embedReviews(@RequestParam(defaultValue = "1000") int limit) {
        return reviewRagService.embedReviews(limit);
    }

    /** 평가용: 같은 후보에서 A(글자)·B(벡터)·C(RAG) 결과를 나란히 (docs/rag-eval/run_eval.py가 부름) */
    @PostMapping("/compare")
    public MoodCompareResponse compareMood(@RequestBody MoodCompareRequest request) {
        return reviewRagService.compareMood(request);
    }

    /** 후보 가게들을 분위기로 줄 세워 보기 (개발·평가용) */
    @PostMapping("/rank")
    public List<MoodMatch> rankByMood(@RequestBody MoodRankRequest request) {
        return reviewRagService.rankByMood(request.getPlaceIds(), request.getMood());
    }
}
