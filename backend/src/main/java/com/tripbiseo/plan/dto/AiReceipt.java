package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini가 영수증 사진에서 읽은 값 (JSON 답 모양). 안 보이면 null */
@Data
public class AiReceipt {
    private String storeName;
    private String paidDate;     // YYYY-MM-DD
    private String paidTime;     // HH:mm
    private String totalAmount;  // 합계(결제 금액). "150,000"처럼 올 수 있어서 글자로 받고 서버가 숫자로 바꿈
    private String address;
    private Boolean isSumDifferent; // (예전) AI가 판단한 합계 다름. 11/16부터는 아래 두 값으로 서버가 계산하고 이 값은 안 씀
    private String itemsTotal;      // 품목 금액을 더한 값 (할인 빼기 전)
    private String discountTotal;   // 할인 줄 금액을 더한 값 (없으면 null)
    private Boolean hasYear;        // 화면에 연도가 보였는지 (없으면 서버가 연도를 정함, 11/16)
}
