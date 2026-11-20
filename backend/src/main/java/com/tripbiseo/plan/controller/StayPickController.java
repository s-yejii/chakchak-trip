package com.tripbiseo.plan.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.plan.dto.StayPick;
import com.tripbiseo.plan.service.StayPickService;

import lombok.RequiredArgsConstructor;

/** ③ 숙소 추가 화면의 근처 추천 숙소 (11/20). PlanController와 겹치지 않게 따로 둔 컨트롤러 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class StayPickController {

    private final StayPickService stayPickService;
    private final LoginUser loginUser;

    /** 여행 지역 가운데 근처 숙소. 예: /api/plans/stays/recommended?lat=37.5638&lng=126.9976 */
    @GetMapping("/stays/recommended")
    public List<StayPick> getStayList(@RequestParam double lat, @RequestParam double lng) {
        return stayPickService.getStayList(loginUser.id(), lat, lng);
    }
}
