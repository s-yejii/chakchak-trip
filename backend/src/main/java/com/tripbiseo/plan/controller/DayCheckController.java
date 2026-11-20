package com.tripbiseo.plan.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.plan.dto.DayCheckRequest;
import com.tripbiseo.plan.dto.DayCheckResponse;
import com.tripbiseo.plan.service.DayCheckService;

import lombok.RequiredArgsConstructor;

/** ③ 담은 장소를 하루에 다 갈 수 있는지 (11/20). PlanController와 겹치지 않게 따로 둔 컨트롤러 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class DayCheckController {

    private final DayCheckService dayCheckService;
    private final LoginUser loginUser;

    /** 예: { "placeIds": ["naver-1", "naver-2"], "dayMinutes": 600 } → 묶음 수·필요한 분·하루를 넘는지 */
    @PostMapping("/day-check")
    public DayCheckResponse checkDay(@RequestBody DayCheckRequest request) {
        return dayCheckService.checkDay(loginUser.id(), request);
    }
}
