package com.tripbiseo.live.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/live/rag/embed 응답: 이번에 임베딩한 리뷰 수(embedded)와 아직 남은 수(remaining) */
@Data
@AllArgsConstructor
public class EmbedResult {
    private int embedded;
    private int remaining;
}
