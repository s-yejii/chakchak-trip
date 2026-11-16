package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * 영업시간을 모르는 곳은 AI 일정 후보에서 빼요 (11/16 ② 찬기, ③ 주형 OK. 장소선정 메모 7번).
 * 전에는 "모르면 통과"라서, 문을 닫았을 수도 있는 곳이 아무 시각에나 들어갔어요.
 *
 * 모르는 곳 = DB 영업시간 글이 비었거나 "점포별 상이"·"전화문의"처럼 시간을 읽을 수 없는 곳.
 *   "상시 개방"·"24시간"은 늘 열린 곳으로 읽어서 안 빠져요 (거리·마을·공원·광장은 DB에 "상시 개방"으로 넣기, 11/6 찬기).
 * 빼지 않는 곳:
 *   - 꼭 갈 곳·가능하면 갈 곳 (사용자가 직접 말한 곳)
 *   - 카카오로 보충한 들를 곳 ("kakao-…"): 원래 영업시간이 없고, 관광지가 모자랄 때만 써서 (메모 6번 ①)
 *   - 숙소: 일정 칸 후보가 아니라 숙소 칸은 따로 정해요
 * 후보에서 아예 빼니까, 장소 고르기(PlanRuleMaker)와 순서 바꾸기(RouteOrderer)는 그대로 둬요.
 * 그래서 직접 고르기 [동선 정리]는 전과 같아요.
 */
@Component
@RequiredArgsConstructor
public class PlanHoursFilter {

    private static final String KAKAO_ID = "kakao-";   // 카카오 보충 장소 번호 앞머리 (PlanCandidateFinder)
    private static final String STAY_KIND = "stay";
    private static final int CHECK_MIN = 12 * 60;      // 영업시간을 읽을 수 있는지만 보려고 낮 12시로 물어봄
    private static final int CHECK_STAY_MIN = 60;
    private static final int MAX_DAYS = 7;             // 요일은 7개라 7일까지만 봄

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlanHoursFilter.class);

    private final PlaceService placeService;               // 영업시간 읽기 (② 메서드를 부르기만 함)
    private final PlanCandidateFinder planCandidateFinder; // 꼭 갈 곳 이름 맞추기

    /** 영업시간을 모르는 곳을 후보에서 뺌. 뺀 수를 돌려줌 */
    public int removeUnknownHours(PlanConditions conditions, Map<String, Place> found, String startDate, int days) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = LocalDate.parse(startDate);
        for (int i = 0; i < Math.min(days, MAX_DAYS); i++) {
            dates.add(date.plusDays(i));
        }
        List<String> removing = new ArrayList<>();
        for (Place place : found.values()) {
            if (isKept(place, conditions)) {
                continue;
            }
            if (isUnknown(place, dates)) {
                removing.add(place.getId());
            }
        }
        for (String placeId : removing) {
            found.remove(placeId);
        }
        if (!removing.isEmpty()) {
            log.info("영업시간 모르는 곳 {}곳 후보에서 뺌", removing.size());
        }
        return removing.size();
    }

    /** 영업시간을 몰라도 남기는 곳: 사용자가 직접 말한 곳, 카카오 보충, 숙소 */
    private boolean isKept(Place place, PlanConditions conditions) {
        boolean isNamed = planCandidateFinder.isMustVisit(place, conditions.getMustVisit())
                || planCandidateFinder.isMustVisit(place, conditions.getWishVisit());
        boolean isKakao = place.getId() != null && place.getId().startsWith(KAKAO_ID);
        return isNamed || isKakao || STAY_KIND.equals(place.getKind());
    }

    /** 여행 날 중 하루라도 영업 여부를 알 수 없으면 모르는 곳 (쉬는 날이면 아는 것) */
    private boolean isUnknown(Place place, List<LocalDate> dates) {
        for (LocalDate date : dates) {
            OpenInfo open = placeService.getOpenInfo(place, date, CHECK_MIN, CHECK_STAY_MIN);
            if (open.getOpen() == null) {
                return true;
            }
        }
        return false;
    }
}
