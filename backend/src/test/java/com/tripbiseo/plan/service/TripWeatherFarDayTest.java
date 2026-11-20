package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.DayWeather;

// 11/20: 기상청 3일 밖 날짜도 날씨가 나와요 (16일 안은 예보, 더 먼 날은 작년 같은 날 관측을 "예년 기준"으로)
class TripWeatherFarDayTest {

    // 진짜 인터넷 대신 정해 둔 값을 돌려주는 가짜 (불린 기간을 기억)
    static class FakeReader extends OpenMeteoReader {
        String asked = "";

        @Override
        public Map<LocalDate, Day> readForecast(double lat, double lng, LocalDate from, LocalDate to) {
            asked += "forecast " + from + "~" + to + ";";
            return makeDays(from, to, 30, null);
        }

        @Override
        public Map<LocalDate, Day> readLastYear(double lat, double lng, LocalDate from, LocalDate to) {
            asked += "lastYear " + from + "~" + to + ";";
            return makeDays(from, to, null, 5.0);
        }

        private Map<LocalDate, Day> makeDays(LocalDate from, LocalDate to, Integer pop, Double rainMm) {
            Map<LocalDate, Day> days = new HashMap<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                Day day = new Day();
                day.minTemp = 4.0;
                day.maxTemp = 13.0;
                day.maxPop = pop;
                day.rainMm = rainMm;
                day.isLastYear = rainMm != null;
                day.weatherCode = 3;
                days.put(date, day);
            }
            return days;
        }
    }

    private final FakeReader reader = new FakeReader();
    // 3일 밖 날짜만 물어서 기상청은 안 불려요 (null로 둬도 돼요)
    private final TripWeatherServiceImpl service = new TripWeatherServiceImpl(null, reader);
    private final LocalDate today = ZonedDateTime.now(Geo.KST).toLocalDate();

    @Test
    void 닷새_뒤는_예보로() {
        List<DayWeather> days = service.getWeatherList(37.56, 126.99, today.plusDays(5), today.plusDays(6));
        assertEquals(2, days.size());
        assertTrue(days.get(0).isAvailable());
        assertEquals("Open-Meteo 예보", days.get(0).getSource());
        assertEquals(30, days.get(0).getMaxPop());
        assertEquals("흐림", days.get(0).getSky());
        assertEquals("자켓 · 트렌치코트 · 니트 · 청바지", days.get(0).getOutfit()); // 낮 평균 어림 10도 (9~11도 줄)
    }

    @Test
    void 사십일_뒤는_작년_같은_날_기준() {
        List<DayWeather> days = service.getWeatherList(37.56, 126.99, today.plusDays(40), today.plusDays(41));
        assertEquals(TripWeatherServiceImpl.LAST_YEAR_SOURCE, days.get(1).getSource());
        assertEquals("비", days.get(1).getSky()); // 작년 그날 5mm 왔음
        assertTrue(days.get(1).getOutfitNote().contains("우산"));
        assertEquals("lastYear " + today.plusDays(40) + "~" + today.plusDays(41) + ";", reader.asked);
    }

    @Test
    void 예보_끝을_걸치면_둘로_나눠_받아요() {
        service.getWeatherList(37.56, 126.99, today.plusDays(13), today.plusDays(17));
        assertEquals("forecast " + today.plusDays(13) + "~" + today.plusDays(15) + ";"
                + "lastYear " + today.plusDays(16) + "~" + today.plusDays(17) + ";", reader.asked);
    }
}
