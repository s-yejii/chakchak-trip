package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * ③ 캡처에서 읽은 날짜의 연도 정하기 (11/16).
 * 예전엔 "연도가 없으면 오늘과 가장 가까운 해"를 AI(Gemini)에게 맡겨서, 영수증 11/15를 2025년으로 읽는 등 틀렸어요.
 * 이제 AI는 "화면에 연도가 있었는지(hasYear)"만 알려 주고, 연도는 서버가 정해요.
 *   - 예약·숙소: 오늘 이후 가장 가까운 그 날짜 (예약은 앞으로 갈 곳). 여행 날짜에 맞추는 건 화면(utils/captureYear.js)이 한 번 더 해요
 *   - 영수증: 오늘 이전 가장 가까운 그 날짜 (영수증은 이미 낸 돈)
 */
public final class CaptureDates {

    private static final int MONTH_DAY_START = 5; // "2026-11-15"에서 "11-15"가 시작하는 자리

    private CaptureDates() {
    }

    /** 오늘 이후(오늘 포함) 가장 가까운 그 월·일. 날짜 글자가 이상하면 그대로 */
    public static String nextFromToday(String date, LocalDate today) {
        LocalDate read = parse(date);
        if (read == null) {
            return date;
        }
        LocalDate candidate = withYearSafe(read, today.getYear());
        if (candidate.isBefore(today)) {
            candidate = withYearSafe(read, today.getYear() + 1);
        }
        return candidate.toString();
    }

    /** 오늘 이전(오늘 포함) 가장 가까운 그 월·일. 날짜 글자가 이상하면 그대로 */
    public static String latestUntilToday(String date, LocalDate today) {
        LocalDate read = parse(date);
        if (read == null) {
            return date;
        }
        LocalDate candidate = withYearSafe(read, today.getYear());
        if (candidate.isAfter(today)) {
            candidate = withYearSafe(read, today.getYear() - 1);
        }
        return candidate.toString();
    }

    /** 체크아웃 날짜를 체크인 다음으로 (12/31 → 1/2처럼 해가 바뀌는 숙박) */
    public static String afterCheckIn(String checkOut, String checkIn) {
        LocalDate out = parse(checkOut);
        LocalDate in = parse(checkIn);
        if (out == null || in == null) {
            return checkOut;
        }
        LocalDate candidate = withYearSafe(out, in.getYear());
        if (!candidate.isAfter(in)) {
            candidate = withYearSafe(out, in.getYear() + 1);
        }
        return candidate.toString();
    }

    private static LocalDate parse(String date) {
        if (date == null || date.length() < MONTH_DAY_START) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 2/29처럼 그해에 없는 날이면 그달 마지막 날로 (LocalDate.withYear가 알아서 맞춤) */
    private static LocalDate withYearSafe(LocalDate date, int year) {
        return date.withYear(year);
    }
}
