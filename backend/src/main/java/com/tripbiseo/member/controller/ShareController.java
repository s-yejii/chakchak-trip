package com.tripbiseo.member.controller;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.member.dto.ShareLinkResponse;
import com.tripbiseo.member.dto.ShareRequest;
import com.tripbiseo.member.dto.SharedTrip;
import com.tripbiseo.member.service.ShareService;

import lombok.RequiredArgsConstructor;

/** ① 일정 공유 (11/16). 링크 만들기는 로그인 필요, 공유 보기(GET)는 로그인 없이 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final LoginUser loginUser;

    /** 내 여행의 공유 링크 만들기 (이미 있으면 같은 링크) */
    @PostMapping
    public ShareLinkResponse createShareLink(@RequestBody ShareRequest request) {
        if (request.getTripId() == null) {
            throw fail(400, "공유할 여행을 골라 주세요.");
        }
        return shareService.createShareLink(loginUser.id(), request.getTripId());
    }

    /** 공유 코드로 일정 보기 (로그인 없이) */
    @GetMapping("/{code}")
    public SharedTrip getSharedTrip(@PathVariable String code) {
        return shareService.getSharedTrip(code);
    }
}
