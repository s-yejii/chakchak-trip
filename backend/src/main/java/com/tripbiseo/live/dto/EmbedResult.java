package com.tripbiseo.live.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/live/rag/embed 응답: 이번에 바꾼 개수 · 아직 남은 개수 */
@Data
@AllArgsConstructor
public class EmbedResult {
    private int embedded;
    private int remaining;
}
