package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * ③ 기상청 단기예보(3일) 밖 날짜의 날씨 (11/20 주형: "1년 뒤 여행도 날씨가 나와야").
 *   - 16일 안: Open-Meteo 예보 (키 없이 쓰는 공개 날씨 API, 여러 나라 기상 모델을 합친 값)
 *   - 그보다 먼 날: 진짜 예보는 없어서 작년 같은 날 실제 관측값을 "예년 기준"으로 (Open-Meteo 과거 관측)
 *   보내는 값은 여행 지역 좌표와 날짜뿐 (회원 정보·내 위치는 안 보냄). 실패하면 빈 결과 → 화면은 예전처럼 안내 문구
 * RestClient: 스프링이 주는 HTTP 요청 도구
 */
@Component
public class OpenMeteoReader {

    static final int FORECAST_DAYS = 16; // Open-Meteo 예보가 있는 날 수 (오늘 포함)
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final String TIMEZONE = "Asia/Seoul";

    private final RestClient restClient = RestClient.create();

    /** 하루 날씨 값 (없는 값은 null) */
    public static class Day {
        public Double maxTemp;
        public Double minTemp;
        public Integer maxPop;      // 강수확률 최고(%), 과거 관측이면 null
        public Double rainMm;       // 강수량(mm), 예보면 null
        public boolean isLastYear;  // 작년 관측값을 올해 날짜로 옮긴 것인지
        public Integer weatherCode; // WMO 날씨 코드 (0 맑음, 1~3 구름, 45·48 안개, 51~67 비, 71~77 눈, 80~82 소나기, 95~ 뇌우)
    }

    /** 예보: 날짜 → 하루 값 (from~to, 16일 안만). 실패하면 빈 맵 */
    public Map<LocalDate, Day> readForecast(double lat, double lng, LocalDate from, LocalDate to) {
        String url = FORECAST_URL + "?latitude=" + lat + "&longitude=" + lng + "&timezone=" + TIMEZONE
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"
                + "&start_date=" + from + "&end_date=" + to;
        return readDays(url, 0);
    }

    /** 예년 기준: 작년 같은 날들의 실제 관측 → 올해 날짜로 바꿔서 돌려줌. 실패하면 빈 맵 */
    public Map<LocalDate, Day> readLastYear(double lat, double lng, LocalDate from, LocalDate to) {
        String url = ARCHIVE_URL + "?latitude=" + lat + "&longitude=" + lng + "&timezone=" + TIMEZONE
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code"
                + "&start_date=" + from.minusYears(1) + "&end_date=" + to.minusYears(1);
        return readDays(url, 1);
    }

    private Map<LocalDate, Day> readDays(String url, int plusYears) {
        Map<LocalDate, Day> days = new HashMap<>();
        try {
            JsonNode daily = restClient.get().uri(url).retrieve().body(JsonNode.class).path("daily");
            JsonNode times = daily.path("time");
            for (int i = 0; i < times.size(); i++) {
                Day day = new Day();
                day.maxTemp = numberAt(daily, "temperature_2m_max", i);
                day.minTemp = numberAt(daily, "temperature_2m_min", i);
                Double pop = numberAt(daily, "precipitation_probability_max", i);
                day.maxPop = pop == null ? null : (int) Math.round(pop);
                day.rainMm = numberAt(daily, "precipitation_sum", i);
                Double code = numberAt(daily, "weather_code", i);
                day.weatherCode = code == null ? null : code.intValue();
                day.isLastYear = plusYears > 0;
                if (day.maxTemp != null && day.minTemp != null) {
                    days.put(LocalDate.parse(times.get(i).asText()).plusYears(plusYears), day);
                }
            }
        } catch (RestClientException | IllegalArgumentException | NullPointerException e) {
            return new HashMap<>(); // 날씨 서버가 답을 못 하면 그 날은 안내 문구로
        }
        return days;
    }

    private Double numberAt(JsonNode daily, String field, int index) {
        JsonNode value = daily.path(field).path(index);
        return value.isNumber() ? value.asDouble() : null;
    }

    /** WMO 날씨 코드 → 화면 글자 (맑음 · 구름많음 · 흐림 · 비 · 눈) */
    static String skyOf(Integer code) {
        if (code == null) {
            return "흐림";
        }
        if (code >= 71 && code <= 77 || code == 85 || code == 86) {
            return "눈";
        }
        if (code >= 51) {
            return "비";
        }
        if (code == 0 || code == 1) {
            return "맑음";
        }
        return code == 2 ? "구름많음" : "흐림";
    }
}
