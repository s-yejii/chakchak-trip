package com.tripbiseo.live.dto;

import lombok.Data;

/** Gemini 답: 질문을 SQL로 바꿀 수 있으면 sql·explain, 없으면 reason */
@Data
public class SqlAnswer {
    private Boolean supported; // SQL로 찾을 수 있는 질문인가
    private String sql;        // supported일 때: SELECT 문 (빈칸 :route·:me·:next·:slack·:prev 사용)
    private String explain;    // supported일 때: 어떻게 찾았는지 한 줄 (화면에 보여 줌)
    private String reason;     // 아닐 때: 왜 못 찾는지 한 줄 (화면에 보여 줌)
    private String mood;       // 분위기 조건 (예: "조용하고 작업하기 좋은"). SQL에 넣지 않고 서버가 리뷰 RAG로 고름. 없으면 null
    private String moodWord;   // 분위기를 리뷰 글자로 찾을 낱말 하나 (예: "조용"). 임베딩이 없을 때 예전 방식(ILIKE)으로 쓰는 대비용
    private String place;      // 질문에 나온 다른 곳 이름 (예: "강남역", "용산구청", "한남동"). 있으면 서버가 :me를 그곳 좌표로 바꿈. 없으면 null
}
