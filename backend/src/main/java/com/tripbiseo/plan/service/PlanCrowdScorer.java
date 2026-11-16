package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.plan.dto.PlaceCrowd;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.mapper.PlanCrowdMapper;
import com.tripbiseo.trip.dto.Place;

import lombok.RequiredArgsConstructor;

/**
 * "조용한"을 골랐을 때만, 평소 사람이 적은 동네에 있는 장소에 점수를 더 줘요 (11/16 ② 찬기, ③ 주형 OK. 장소선정 메모 14번).
 * 전에는 리뷰에 "조용"이라는 낱말이 있는지만 봐서, 사람 많은 거리 한복판 가게도 그대로 나왔어요.
 *
 * 기준 (PlanVerifier 확인 줄의 한산·보통·붐빔과 같은 문턱):
 *   한산(서울 순위 50 미만)   +0.3   = 리뷰 태그 하나와 같은 크기 (1km 더 가도 되는 만큼)
 *   보통(50~79)              +0.15
 *   붐빔(80 이상)            0
 *   데이터 없는 곳           +0.15  (보통과 같게: 데이터가 없다고 손해도 이득도 없게)
 * 고른 동(+1.0)·가능하면 갈 곳(+2.0)보다 작아서, 조용한 곳을 찾겠다고 고른 동네 밖으로 나가지는 않아요.
 *
 * 붐빔 값은 "여행 가는 요일의 낮(10~21시) 평균"이에요. 점수는 칸 시각을 정하기 전에 매겨서 도착 시각을 아직 몰라요.
 * (시각별로 따로 봐도 91%는 같은 단계였어요. 실제 시각의 붐빔은 확인 줄에 따로 나와요.)
 */
@Component
@RequiredArgsConstructor
public class PlanCrowdScorer {

    private static final String QUIET_TAG = "quiet";   // 화면 "조용한" 칩
    private static final int BUSY_PCT = 80;            // 서울 순위 이 이상이면 붐빔 (PlanVerifier와 같음)
    private static final int NORMAL_PCT = 50;          // 이 이상이면 보통, 아래면 한산
    private static final double QUIET_BONUS = 0.3;     // 한산한 동네
    private static final double NORMAL_BONUS = 0.15;   // 보통이거나 데이터가 없는 동네
    private static final int MAX_DAYS = 7;             // 요일은 7개라 7일까지만 봄

    private final PlanCrowdMapper planCrowdMapper; // 250m 칸 평소 붐빔 (② 찬기 표 읽기만)

    /** "조용한"을 골랐으면 후보마다 붐빔 점수를 더함 */
    public void addQuietBonus(PlanConditions conditions, Map<String, Place> found, Map<String, Double> scores,
                              String startDate, int days) {
        if (conditions.getTags() == null || !conditions.getTags().contains(QUIET_TAG) || found.isEmpty()) {
            return; // 안 골랐으면 붐빔은 점수에 안 씀
        }
        Map<String, Integer> crowds = findCrowds(found, startDate, days);
        for (Place place : found.values()) {
            Integer seoulPct = crowds.get(place.getId());
            double bonus = bonusOf(seoulPct);
            scores.put(place.getId(), scores.getOrDefault(place.getId(), 0.0) + bonus);
        }
    }

    /** 붐빔 단계 → 점수. 데이터가 없으면(null) 보통과 같게 */
    double bonusOf(Integer seoulPct) {
        if (seoulPct == null) {
            return NORMAL_BONUS;
        }
        if (seoulPct >= BUSY_PCT) {
            return 0;
        }
        return seoulPct >= NORMAL_PCT ? NORMAL_BONUS : QUIET_BONUS;
    }

    /** 후보 장소들의 여행 요일 낮 평균 붐빔 (장소 번호 → 서울 순위). 칸이 없는 곳은 빠짐 */
    private Map<String, Integer> findCrowds(Map<String, Place> found, String startDate, int days) {
        List<Integer> dayKinds = new ArrayList<>();
        LocalDate date = LocalDate.parse(startDate);
        for (int i = 0; i < Math.min(days, MAX_DAYS); i++) {
            int dayKind = date.plusDays(i).getDayOfWeek().getValue(); // 1=월 … 7=일
            if (!dayKinds.contains(dayKind)) {
                dayKinds.add(dayKind);
            }
        }
        List<String> placeIds = new ArrayList<>(found.keySet());
        Map<String, Integer> crowds = new HashMap<>();
        for (PlaceCrowd crowd : planCrowdMapper.selectDayCrowds(placeIds, dayKinds)) {
            crowds.put(crowd.getPlaceId(), crowd.getSeoulPct());
        }
        return crowds;
    }
}
