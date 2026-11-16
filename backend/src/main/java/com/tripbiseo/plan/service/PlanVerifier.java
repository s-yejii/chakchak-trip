package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.CrowdHour;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.plan.mapper.PlanCrowdMapper;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 검증 줄 (11/13): 다 짠 일정에서 예약(고정)·꼭 갈 곳마다 우리 데이터로 다시 확인한 한 줄을 안내 목록에 더함.
 * 예) 첫 줄 "✓ 산청숯불가든 을지로 · 11/16(월) 19:00" / 둘째 줄 "영업 중 · 19시 붐빔(서울 상위 8%) · 숙소에서 15분"
 * 11/14 디자인 검토: 한 줄에 정보가 5개라 좁은 폰에서 중간에 꺾여서, 첫 줄(이름·날짜·시각)과 둘째 줄(확인한 것)을 줄바꿈으로 나눔
 * AI가 고른 걸 DB 영업시간·거리로 한 번 더 확인해서 보여 주는 것 (AI가 지어낸 게 아님을 화면에서 보여 줌).
 * 영업시간을 모르면 "영업시간 정보 없음"이라고 그대로 말함 (모르는 걸 맞다고 하지 않음)
 */
@Component
@RequiredArgsConstructor
public class PlanVerifier {

    private static final String STAY_CATEGORY = "숙소";
    private static final String MUST = "must";
    private static final int MAX_LINES = 6; // 안내 목록이 너무 길어지지 않게
    private static final int MIN_FACT_REVIEWS = 10;
    private static final String NO_HOURS_TEXT = "영업시간 확인 못 함";
    private static final String CLOSED_TEXT = "그 시각 영업 안 해요";
    private static final java.util.regex.Pattern BOARDING = java.util.regex.Pattern.compile("역( |$)|역사|터미널|공항|정차역"); // 역·터미널 // 이 이상 리뷰가 있어야 평점을 이유로 씀

    private static final String UNLOCATED_MARK = "위치 확인 필요"; // 위치를 못 찾은 예약 칸 메모 앞부분 (PlanServiceImpl)
    private static final int CROWD_FIRST_HOUR = 7;  // 생활인구 데이터는 7~23시만
    private static final int CROWD_LAST_HOUR = 23;
    private static final int BUSY_PCT = 80;   // 서울 순위 이 이상이면 붐빔
    private static final int NORMAL_PCT = 50; // 이 이상이면 보통, 아래면 한산

    private final PlaceService placeService; // 영업시간 확인 (② 메서드를 부르기만 함)
    private final PlanCrowdMapper planCrowdMapper; // 250m 칸 평소 붐빔 (② 찬기 표 읽기만)

    /** 예약·꼭 갈 곳마다 검증 한 줄을 response 안내(notes)에 더함 */
    public void addVerifyNotes(PlanGenerateResponse response) {
        if (response.getStartDate() == null) {
            return;
        }
        LocalDate start = LocalDate.parse(response.getStartDate());
        List<String> lines = new ArrayList<>();
        for (Stop stop : response.getStops()) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            if (isTarget(stop) && !isUnlocated(stop)) {
                String line = makeLine(stop, start.plusDays(dayOf(stop) - 1), response.getStops());
                lines.add(line);
                addClosedWarning(response, stop, line);
            }
        }
        for (String line : lines) {
            PlanNote note = new PlanNote();
            note.setText(line);
            response.getNotes().add(note);
        }
    }

    /**
     * 이유 줄이 빈 칸에 서버가 아는 사실로 한 줄 채우기 (11/14). 예) "평점 4.8 · 리뷰 320개 · 앞 장소에서 이동 7분"
     * AI가 이유를 안 쓰거나(대체 일정은 전부) 근거 없는 말이라 뺀 칸이 절반 넘게 빈칸이었어요.
     * 평점은 리뷰가 10개 이상일 때만, 이동 분은 시각 순으로 바로 앞 칸에서 (일정 다 짜고 정렬한 뒤에 불러야 맞아요)
     */
    public void fillEmptyMemos(PlanGenerateResponse response) {
        Stop previous = null;
        for (Stop stop : response.getStops()) {
            boolean isSameDay = previous != null && dayOf(previous) == dayOf(stop);
            boolean isEmpty = stop.getMemo() == null || stop.getMemo().isBlank();
            if (isEmpty && !STAY_CATEGORY.equals(stop.getCategory())) { // 11/16: 꼭 갈 곳·예약도 (예전엔 고정 칸이라 빈칸으로 남았어요)
                String memo = factMemoOf(stop, isSameDay ? previous : null);
                stop.setMemo(memo.isEmpty() ? null : memo);
            }
            previous = stop;
        }
    }

    private String factMemoOf(Stop stop, Stop previous) {
        List<String> parts = new ArrayList<>();
        Place place = stop.getPlaceId() == null ? null : findPlace(stop.getPlaceId());
        if (place != null && place.getRating() != null && place.getReviewCount() != null && place.getReviewCount() >= MIN_FACT_REVIEWS) {
            parts.add("평점 " + place.getRating() + " · 리뷰 " + place.getReviewCount() + "개");
        }
        boolean hasPoints = previous != null && previous.getLat() != null && stop.getLat() != null;
        if (hasPoints) {
            int minutes = PlanTravelTime.minutesBetween(new double[] {previous.getLat(), previous.getLng()}, new double[] {stop.getLat(), stop.getLng()});
            parts.add("앞 장소에서 이동 " + minutes + "분");
        }
        return String.join(" · ", parts);
    }

    /**
     * 예약인데 그 시각에 영업을 안 하면 안내 맨 위에 경고 한 줄 (11/16).
     * 예전엔 확인 줄 끝에 "그 시각 영업 안 해요"만 작게 붙어서 09:30 예약(영업 전)이 눈에 안 띄었어요. 사용자가 넣은 예약이라 지우지는 않음
     */
    private void addClosedWarning(PlanGenerateResponse response, Stop stop, String line) {
        if (!Boolean.TRUE.equals(stop.getFixed()) || !line.contains(CLOSED_TEXT)) {
            return;
        }
        PlanNote warning = new PlanNote();
        warning.setText(stop.getName() + " 예약 " + stop.getTime() + "은 영업시간이 아니에요 · 예약 시각을 확인해 주세요");
        response.getNotes().add(0, warning);
    }

    /** 예약(고정)이거나 꼭 갈 곳인 칸 (숙소 칸은 뺌) */
    private boolean isTarget(Stop stop) {
        boolean isImportant = Boolean.TRUE.equals(stop.getFixed()) || MUST.equals(stop.getImportance());
        return isImportant && !STAY_CATEGORY.equals(stop.getCategory()) && stop.getTime() != null;
    }

    /**
     * 위치를 못 찾은 예약 칸 (PlanServiceImpl이 메모 앞에 "위치 확인 필요"를 붙임).
     * 확인 못 한 칸에 ✓ 줄을 붙이면 틀린 말이라 안 만들어요. 그 칸은 위에 "위치를 찾지 못했어요" 안내 한 줄이 이미 있어요 (11/14)
     */
    private boolean isUnlocated(Stop stop) {
        return stop.getMemo() != null && stop.getMemo().startsWith(UNLOCATED_MARK);
    }

    private String makeLine(Stop stop, LocalDate date, List<Stop> stops) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN); // "월"
        String title = "✓ " + stop.getName() + " · " + date.getMonthValue() + "/" + date.getDayOfMonth()
            + "(" + weekday + ") " + stop.getTime();
        List<String> checks = new ArrayList<>(); // 둘째 줄: 확인한 것들
        String openText = openTextOf(stop, date);
        if (openText != null) {
            checks.add(openText);
        } else if (!isBoarding(stop)) {
            checks.add(NO_HOURS_TEXT); // 장소 번호가 없는 예약(카카오로 찾은 곳): 영업은 확인 못 했다고 그대로 말함 (11/14)
        }
        String crowdText = crowdTextOf(stop, date);
        if (crowdText != null) {
            checks.add(crowdText);
        }
        Integer minutes = minutesFromStay(stop, stops);
        if (minutes != null) {
            checks.add("숙소에서 " + minutes + "분");
        }
        if (checks.isEmpty()) {
            return title;
        }
        return title + "\n" + String.join(" · ", checks); // 화면(GeneratingView)이 줄바꿈으로 두 줄로 나눠 보여 줘요
    }

    /** 그 날·그 시각 영업: "영업" / "영업 안 해요" / "영업시간 정보 없음". 장소 번호가 없으면(역·터미널 등) null */
    private String openTextOf(Stop stop, LocalDate date) {
        if (stop.getPlaceId() == null) {
            return null;
        }
        Place place = findPlace(stop.getPlaceId());
        if (place == null) {
            return null;
        }
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        OpenInfo open = placeService.getOpenInfo(place, date, Geo.toMinutes(stop.getTime()), stay);
        if (open.getOpen() == null) {
            return "영업시간 정보 없음";
        }
        return Boolean.TRUE.equals(open.getOpen()) ? "영업 중" : CLOSED_TEXT;
    }

    /**
     * 그 요일·시각에 이 장소가 든 250m 칸이 평소 얼마나 붐비는지 (서울 생활인구, 찬기 표).
     * 데이터가 없는 곳(중구·강남·종로 밖)·시각(7~23시 밖)이면 null = 줄에 안 붙임 (가짜 값 안 만듦)
     */
    private String crowdTextOf(Stop stop, LocalDate date) {
        if (stop.getLat() == null || stop.getLng() == null) {
            return null;
        }
        int hour = Geo.toMinutes(stop.getTime()) / 60;
        if (hour < CROWD_FIRST_HOUR || hour > CROWD_LAST_HOUR) {
            return null;
        }
        int dayKind = date.getDayOfWeek().getValue(); // 1=월 … 7=일 (공휴일 8은 달력이 없어서 안 씀)
        CrowdHour crowd = planCrowdMapper.selectCrowdHour(stop.getLat(), stop.getLng(), dayKind, hour);
        if (crowd == null || crowd.getSeoulPct() == null) {
            return null;
        }
        return hour + "시 " + crowdLevelText(crowd.getSeoulPct());
    }

    /**
     * 서울 순위(같은 요일·시각 서울 칸 중 0~100, 높을수록 붐빔)를 세 단계 말로 (11/13 주형 A안).
     * 80 이상 "붐빔(서울 상위 N%)" · 50~79 "보통" · 50 미만 "한산"
     */
    String crowdLevelText(int seoulPct) {
        if (seoulPct >= BUSY_PCT) {
            return "붐빔(서울 상위 " + Math.max(1, 100 - seoulPct) + "%)";
        }
        return seoulPct >= NORMAL_PCT ? "보통" : "한산";
    }

    /**
     * 장소 번호로 DB 장소 찾기. 카카오에서 찾은 곳("kakao-남대문시장")처럼 DB에 없으면 null.
     * 11/14: 예전엔 여기서 오류(404)가 그대로 나가서 일정 만들기가 통째로 실패했어요. 확인 줄은 덤이라 못 찾으면 그냥 건너뜀
     */
    private Place findPlace(String placeId) {
        try {
            return placeService.getPlace(placeId);
        } catch (ApiException e) {
            return null;
        }
    }

    /** 역·터미널·공항 칸인지 (영업시간을 따질 곳이 아님) */
    private boolean isBoarding(Stop stop) {
        String text = (stop.getName() == null ? "" : stop.getName()) + " " + (stop.getCategory() == null ? "" : stop.getCategory());
        return BOARDING.matcher(text).find();
    }

    /** 그날 숙소(그날 체크인 전이면 전날 숙소)에서 이 칸까지 어림 이동 분. 숙소가 없으면 null */
    private Integer minutesFromStay(Stop stop, List<Stop> stops) {
        Stop stay = findStay(stops, dayOf(stop));
        if (stay != null && stay.getTime() != null && stop.getTime() != null && stop.getTime().compareTo(stay.getTime()) < 0) {
            stay = null; // 그날 체크인 전 칸이면 그날 숙소에서 오는 게 아님 (11/14: 12:30 점심에 "숙소에서 6분", 체크인 15:00)
        }
        if (stay == null) {
            stay = findStay(stops, dayOf(stop) - 1);
        }
        if (stay == null || stay.getLat() == null || stop.getLat() == null) {
            return null;
        }
        return PlanTravelTime.minutesBetween(new double[] {stay.getLat(), stay.getLng()}, new double[] {stop.getLat(), stop.getLng()});
    }

    private Stop findStay(List<Stop> stops, int day) {
        for (Stop stop : stops) {
            if (STAY_CATEGORY.equals(stop.getCategory()) && dayOf(stop) == day && stop.getLat() != null) {
                return stop;
            }
        }
        return null;
    }

    private int dayOf(Stop stop) {
        return stop.getDay() == null ? 1 : stop.getDay();
    }
}
