package com.tripbiseo.plan.controller;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.plan.dto.HangangBusOption;
import com.tripbiseo.plan.service.HangangBusService;

import lombok.RequiredArgsConstructor;

/** ③ 한강버스 추천 (11/16). 길 안내(⑤ RouteGuideView)에서 다음 칸이 예약일 때 부름 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class HangangBusController {

    private static final String HHMM = "\\d{1,2}:\\d{2}"; // 15:00

    private final HangangBusService hangangBusService;
    private final LoginUser loginUser;

    /**
     * 예약까지 시간이 많이 뜰 때 한강버스로 가는 방법 하나. 권할 게 없으면 204(내용 없음).
     * 예: /api/plans/hangang-bus?fromLat=37.5616&fromLng=126.9827&toLat=37.5126&toLng=127.1020&arriveBy=15:00
     */
    @GetMapping("/hangang-bus")
    public ResponseEntity<HangangBusOption> getOption(@RequestParam double fromLat, @RequestParam double fromLng,
            @RequestParam double toLat, @RequestParam double toLng, @RequestParam String arriveBy,
            @RequestParam(required = false) String now) {
        loginUser.id(); // 로그인 확인 (안 했으면 401)
        if (!arriveBy.matches(HHMM) || (now != null && !now.matches(HHMM))) {
            throw fail(400, "시각은 15:00처럼 보내 주세요.");
        }
        HangangBusOption option = hangangBusService.getOption(fromLat, fromLng, toLat, toLng, arriveBy, now);
        return option == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(option);
    }
}
