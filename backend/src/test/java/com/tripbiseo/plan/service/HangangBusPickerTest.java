package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.HangangBusTime;

// 한강버스 추천 규칙 (11/16). 시간표는 ① 상재 db/24_hangang_bus.sql을 그대로 읽어서 써요 (DB 없이)
// 시연 값: 명동 숙소 12:40 출발 → 옥수 선착장 13:03 도착(대중교통 23분) → 13:13 배 → 잠실 14:03 → 버스 25분 → 14:28 스시이세이
class HangangBusPickerTest {

    private static final int NOW = Geo.toMinutes("12:40");
    private static final Map<String, Integer> READY_AT = Map.of("hangang-oksu", NOW + 23);   // 숙소 → 옥수 선착장
    private static final Map<String, Integer> AFTER_ALIGHT = Map.of("hangang-jamsil", 25);   // 잠실 선착장 → 식당 (버스)
    private static final int DIRECT_ARRIVE = NOW + 45;                                       // 숙소 → 식당 바로 (13:25)

    private static List<HangangBusTime> times;

    @BeforeAll
    static void readTimetable() throws IOException {
        times = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("../db/24_hangang_bus.sql"), StandardCharsets.UTF_8)) {
            String text = line.trim();
            if (text.startsWith("('동부'") || text.startsWith("('서부'")) {
                times.add(toTime(text));
            }
        }
    }

    // "('동부','hangang-oksu','잠실행',1,793,FALSE),  -- 13:13" → 시간표 한 줄
    private static HangangBusTime toTime(String text) {
        String inside = text.substring(1, text.indexOf(')'));
        String[] parts = inside.replace("'", "").split(",");
        HangangBusTime time = new HangangBusTime();
        time.setRoute(parts[0]);
        time.setPierId(parts[1]);
        time.setDirection(parts[2]);
        time.setRunNo(Integer.parseInt(parts[3]));
        time.setTimeMin(Integer.parseInt(parts[4]));
        time.setArrival(parts[5].equals("TRUE"));
        return time;
    }

    @Test
    void 시연_15시_예약이면_옥수_13시13분_배() {
        HangangBusPicker.Choice choice = HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("15:00"), DIRECT_ARRIVE, null);
        assertNotNull(choice);
        assertEquals("13:13", Geo.toHhmm(choice.board.getTimeMin()));
        assertEquals("14:03", Geo.toHhmm(choice.alight.getTimeMin()));
        assertEquals("14:28", Geo.toHhmm(choice.doorArrive));
    }

    @Test
    void 예약_14시30분이면_10분_전까지_못_가서_안_보임() {
        assertNull(HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("14:30"), DIRECT_ARRIVE, null));
    }

    @Test
    void 배에서_내리기_전에_비가_오면_안_보임() {
        assertNull(HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("15:00"), DIRECT_ARRIVE, Geo.toMinutes("13:00")));
    }

    @Test
    void 비가_내린_뒤에_오면_보임() {
        assertNotNull(HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("15:00"), DIRECT_ARRIVE, Geo.toMinutes("16:00")));
    }

    @Test
    void 숙소를_늦게_나와_13시13분_배를_놓치면_안_보임() {
        Map<String, Integer> late = Map.of("hangang-oksu", Geo.toMinutes("13:10")); // 다음 옥수 배는 15:15라 예약에 늦음
        assertNull(HangangBusPicker.pick(times, late, AFTER_ALIGHT, Geo.toMinutes("15:00"), DIRECT_ARRIVE, null));
    }

    @Test
    void 바로_가도_30분_안_기다리면_안_보임() {
        assertNull(HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("15:00"), Geo.toMinutes("14:40"), null));
    }

    @Test
    void 길찾기가_어림값이면_10분_더해서_빠듯한_예약은_안_보임() {
        Route guessed = new Route(); // TMAP·ODsay가 안 돼 거리로 어림한 잠실 선착장 → 식당 (11/16 실제로 16분이 나옴, 네이버는 23~25분)
        guessed.setMinutes(16);
        guessed.setMode("transit");
        guessed.setSource("estimate");
        assertEquals(26, HangangBusServiceImpl.minutesOf(guessed));
        Map<String, Integer> afterGuess = Map.of("hangang-jamsil", HangangBusServiceImpl.minutesOf(guessed)); // 14:03 + 26 = 14:29 도착
        assertNull(HangangBusPicker.pick(times, READY_AT, afterGuess, Geo.toMinutes("14:30"), DIRECT_ARRIVE, null));
        assertNotNull(HangangBusPicker.pick(times, READY_AT, afterGuess, Geo.toMinutes("15:00"), DIRECT_ARRIVE, null));
        guessed.setSource("odsay"); // 진짜 길찾기 값은 그대로
        assertEquals(16, HangangBusServiceImpl.minutesOf(guessed));
    }

    @Test
    void 여유가_많으면_선착장_근처에서_쉬었다_가기() {
        HangangBusPicker.Choice choice = HangangBusPicker.pick(times, READY_AT, AFTER_ALIGHT, Geo.toMinutes("15:00"), DIRECT_ARRIVE, null);
        int rest = HangangBusPicker.restMinutes(Geo.toMinutes("15:00"), choice.doorArrive); // 14:45 − 14:28 = 17분 → 15분
        assertEquals(15, rest);
        assertEquals("14:18", Geo.toHhmm(choice.alight.getTimeMin() + rest)); // 14:03 배 내려서 15분 쉬고 버스
        assertEquals("14:43", Geo.toHhmm(choice.doorArrive + rest));          // 예약 17분 전 도착
        assertEquals(0, HangangBusPicker.restMinutes(Geo.toMinutes("14:40"), choice.doorArrive)); // 빠듯하면 안 쉼
        assertEquals(30, HangangBusPicker.restMinutes(Geo.toMinutes("17:00"), choice.doorArrive)); // 최대 30분
    }

    @Test
    void 버스_번호_찾기() {
        assertEquals("3317", HangangBusServiceImpl.findBusNo("버스 3317 잠일고등학교→잠실역.롯데월드 6정거장"));
        assertEquals("3323", HangangBusServiceImpl.findBusNo("2호선 잠실새내→잠실 1정거장 · 버스 3323 한강버스잠실선착장→잠실역 13정거장"));
        assertNull(HangangBusServiceImpl.findBusNo("2호선 잠실새내→잠실 1정거장"));
        assertNull(HangangBusServiceImpl.findBusNo(null));
    }
}
