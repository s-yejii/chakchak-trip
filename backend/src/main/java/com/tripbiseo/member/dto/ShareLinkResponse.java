package com.tripbiseo.member.dto;

import lombok.Data;

/** 공유 링크 (POST /api/share 응답). 화면이 location.origin + path로 링크를 만들어요 */
@Data
public class ShareLinkResponse {
    private String code; // 공유 코드 (22자, 맞히기 어려운 글자)
    private String path; // "/s/" + code
}
