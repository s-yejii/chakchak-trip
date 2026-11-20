package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.dev.DevService;
import com.tripbiseo.plan.dto.StayPick;
import com.tripbiseo.plan.mapper.StayPickMapper;

import lombok.RequiredArgsConstructor;

/**
 * ③ 근처 추천 숙소 (11/20 주형: "숙소 예약에서 숙소 추천도 같이").
 * 우리 DB의 숙박은 관광공사 등록분뿐이고 평점이 없어서, 지어낸 점수 없이 "사진 있는 호텔 → 여행 지역에서 가까운 순"으로 골라요.
 * 시연 계정은 시나리오 숙소(르메르디앙 서울 명동)를 맨 위에 두고 나머지는 같은 기준으로 아래에.
 */
@Service
@RequiredArgsConstructor
public class StayPickServiceImpl implements StayPickService {

    static final int RADIUS_M = 2000;      // 여행 지역 가운데에서 이 안의 숙소만
    static final int WIDE_RADIUS_M = 5000; // 2km 안에 하나도 없으면 여기까지 넓혀요 (DB 숙박이 도심에 몰려 있어서)
    static final int MAX_COUNT = 5;   // 화면에 보여 줄 수

    private final StayPickMapper stayPickMapper;
    private final DevService devService;
    private final DemoScenario demoScenario;

    @Override
    public List<StayPick> getStayList(long memberId, double lat, double lng) {
        List<StayPick> list = stayPickMapper.selectStayList(lat, lng, RADIUS_M, MAX_COUNT);
        if (list.isEmpty()) {
            list = stayPickMapper.selectStayList(lat, lng, WIDE_RADIUS_M, MAX_COUNT);
        }
        if (!devService.isDemoAccount(memberId)) {
            return list;
        }
        StayPick demoStay = stayPickMapper.selectStay(demoScenario.getStayPlaceId(), lat, lng);
        return putFirst(demoStay, list);
    }

    /** first를 맨 위에 두고, 목록에 이미 있으면 그 칸은 빼요 (수는 MAX_COUNT 그대로) */
    List<StayPick> putFirst(StayPick first, List<StayPick> list) {
        if (first == null) {
            return list;
        }
        List<StayPick> result = new ArrayList<>();
        result.add(first);
        for (StayPick stay : list) {
            if (!stay.getPlaceId().equals(first.getPlaceId()) && result.size() < MAX_COUNT) {
                result.add(stay);
            }
        }
        return result;
    }
}
