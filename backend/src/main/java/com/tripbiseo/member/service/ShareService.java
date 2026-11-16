package com.tripbiseo.member.service;

import com.tripbiseo.member.dto.ShareLinkResponse;
import com.tripbiseo.member.dto.SharedTrip;

/** ① 일정 공유: 카카오톡으로 보낸 링크를 로그인 없이 열어 일정만 보게 해요 (11/16) */
public interface ShareService {

    /** 내 여행의 공유 링크 (이미 있으면 같은 링크) */
    ShareLinkResponse createShareLink(long memberId, long tripId);

    /** 공유 코드로 일정 보기 (로그인 없이). 없는 코드면 404 */
    SharedTrip getSharedTrip(String code);
}
