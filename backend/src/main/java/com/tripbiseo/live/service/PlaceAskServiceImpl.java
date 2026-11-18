package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.FoundArea;
import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.common.external.LocationSearchClient;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AskSituation;
import com.tripbiseo.live.dto.AskedPlace;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.MoodMatch;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.PlaceAskResult;
import com.tripbiseo.live.dto.SqlAnswer;
import com.tripbiseo.live.dto.SqlCheck;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 AI에게 묻기 (Text-to-SQL).
 * 순서: 상황 모으기 → Gemini가 SQL 작성 → SqlChecker 검사(떨어지면 한 번 더) → AskSqlRunner로 실행
 *       → (덜 돌아가는·가까운 순이면) WalkRanker가 실제 걷는 길로 다시 줄 세움 → 영업·여유 계산.
 * 이어지는 질문("이 중에 …", 11/13)이면 앞 결과 id를 빈칸 :prev로 넣어 그 안에서만 찾아요.
 * 어디서 막히면 fallback = true를 돌려주고, 부르는 쪽(LiveServiceImpl.assist)이 예전 반경 고르기로 이어 가요.
 * (여기서 예전 추천을 직접 부르지 않는 이유: LiveServiceImpl이 이 서비스를 부르니 서로 부르는 구조가 됨)
 * 좌표는 Gemini에 보내지 않아요. SQL에는 빈칸 이름만 있고 값은 서버가 넣어요.
 */
@Service
@RequiredArgsConstructor
public class PlaceAskServiceImpl implements PlaceAskService {

    private static final Logger log = LoggerFactory.getLogger(PlaceAskServiceImpl.class); // 왜 못 찾았는지 남김 (좌표·질문 원문은 안 남김)

    private static final double MIN_RATING = 4.0; // 이 보정 평점 아래는 결과에서 뺌 (11/17)
    private static final int STAY_MINUTES = CafeStay.BASE_MIN; // 들르면 머무는 시간(분) 30. 프롬프트 규칙과 같음. 카페는 여유가 많으면 CafeStay 규칙으로 늘림 (11/17)
    private static final int BAND_METERS = 190;  // 지도 띠 폭: 가는 길 양옆 190m = 걸어서 왕복 약 5분
    private static final String ROUTE = "route";
    private static final String ME = "me";
    private static final String NEXT = "next";
    private static final String SLACK = "slack";
    private static final String PREV = "prev";
    private static final int MAX_PREV_IDS = 10;  // 앞 결과는 많아야 10곳 (AI SQL LIMIT 상한과 같음)
    private static final int MOOD_WALK_POOL = 5; // 분위기로 고른 뒤 실제 걷는 길로 다시 잴 곳 수 (길찾기 호출 수 때문에, SqlChecker RERANK_POOL과 같음)

    private final LiveTripReader liveTripReader;   // 진행 중인 여행인지 확인 (④ 도우미)
    private final StatusService statusService;     // 다음 칸·가는 길·여유 (④)
    private final RouteClient routeClient;         // 걸어가는 구간인지 (공통)
    private final AiService aiService;             // Gemini (공통)
    private final SqlPromptMaker sqlPromptMaker;
    private final SqlChecker sqlChecker;
    private final AskSqlRunner askSqlRunner;
    private final PlaceService placeService;       // 영업 중인지 (②)
    private final WalkRanker walkRanker;           // 실제 걷는 길로 다시 줄 세우기 (11/13)
    private final ReviewRagService reviewRagService; // 분위기 조건은 리뷰 RAG로 고르기 (11/15)
    private final LocationSearchClient locationSearchClient; // "강남역 근처"처럼 다른 곳 이름 → 좌표 (카카오 검색, 공통, 11/18)

    /** 사용자 말로 장소 찾기 (prevPlaceIds가 있으면 그 안에서만) */
    @Override
    public PlaceAskResult findPlaces(long memberId, long tripId, String message, String prevMessage, List<String> prevPlaceIds,
                                     double lat, double lng) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, lat, lng);
        StatusResponse status = statusService.getStatus(memberId, tripId, toLocation(lat, lng));
        AskSituation situation = collectSituation(trip, status, lat, lng, prevPlaceIds);
        Set<String> usable = findUsable(situation);

        SqlAnswer answer = askSql(message, prevMessage, usable, null);
        if (answer == null) {
            return makeFallback();
        }
        if (!Boolean.TRUE.equals(answer.getSupported())) {
            log.info("AI에게 묻기: 표에 없는 조건 - {}", answer.getReason());
            return makeUnsupported(answer);
        }
        if (hasPlace(answer)) {
            if (!moveToPlace(situation, answer.getPlace().trim())) {
                return makePlaceNotFound(answer.getPlace().trim());
            }
            usable = findUsable(situation); // 다른 곳 기준이면 가는 길·다음 장소·여유는 빠지고 :me(그곳)만 남음
        }
        SqlCheck check = checkAnswer(answer, usable);
        if (!check.isOk()) {
            answer = askSql(message, prevMessage, usable, check.getReason()); // 한 번 더: 떨어진 이유를 붙여서
            check = answer == null ? check : checkAnswer(answer, usable);
        }
        if (answer == null || !check.isOk()) {
            log.warn("AI에게 묻기 fallback: SQL 검사 두 번 탈락 - {} / SQL: {}", check.getReason(), answer == null ? null : answer.getSql());
            return makeFallback();
        }
        return runAndFinish(answer, check, situation);
    }

    // ---------------------------------------------------------------- 1. 상황 모으기

    /** 빈칸 값: 내 위치는 항상, 다음 칸·여유는 남은 일정이 있으면, 가는 길은 걸어가는 구간일 때만, 앞 결과는 이어지는 질문일 때만 */
    private AskSituation collectSituation(Trip trip, StatusResponse status, double lat, double lng, List<String> prevPlaceIds) {
        AskSituation situation = new AskSituation();
        situation.setMe(toPoint(lat, lng));
        situation.setMeLat(lat);
        situation.setMeLng(lng);
        situation.setPrev(toPrev(prevPlaceIds));
        if (status.getArea() != null) {
            situation.setSlack(status.getArea().getSlackMin());
        }
        Stop nextStop = findNextStop(trip, status.getNext());
        if (nextStop == null) {
            return situation;
        }
        situation.setNext(toPoint(nextStop.getLat(), nextStop.getLng()));
        situation.setNextLat(nextStop.getLat());
        situation.setNextLng(nextStop.getLng());
        List<double[]> coords = status.getNext().getCoords();
        boolean hasRoute = coords != null && coords.size() >= 2;
        if (hasRoute && isWalkingToNext(trip, nextStop, lat, lng)) {
            situation.setRoute(toLine(coords));
        }
        return situation;
    }

    /** 지금 상황의 다음 칸 (좌표 없으면 null) */
    private Stop findNextStop(Trip trip, NextRoute next) {
        if (next == null || next.getId() == null) {
            return null;
        }
        for (Stop stop : trip.getStops()) {
            if (next.getId().equals(stop.getId()) && stop.getLat() != null && stop.getLng() != null) {
                return stop;
            }
        }
        return null;
    }

    /** 다음 칸까지 걸어가는지 (길찾기와 같은 규칙: 고른 수단, 없으면 700m 안은 도보). 대중교통이면 "가는 길" 근처는 의미 없음 */
    private boolean isWalkingToNext(Trip trip, Stop nextStop, double lat, double lng) {
        double meters = Geo.distanceMeters(lat, lng, nextStop.getLat(), nextStop.getLng());
        return "walk".equals(routeClient.chooseMode(nextStop.getMode(), meters, trip.getTransports()));
    }

    /** 질문에 다른 곳 이름(강남역·용산구청·한남동 등)이 있는지 */
    private boolean hasPlace(SqlAnswer answer) {
        return answer.getPlace() != null && !answer.getPlace().isBlank();
    }

    /**
     * 다른 곳 기준으로 찾기 (11/18): :me를 그곳 좌표로 바꾸고, 내 일정에 묶인 값(가는 길·다음 장소·여유)은 지움.
     * 그곳을 못 찾으면 false (상황은 그대로)
     */
    private boolean moveToPlace(AskSituation situation, String place) {
        double[] point = findPlacePoint(place, situation.getMeLat(), situation.getMeLng());
        if (point == null) {
            log.info("AI에게 묻기: 장소 이름 '{}'을 찾지 못함", place);
            return false;
        }
        situation.setMe(toPoint(point[0], point[1]));
        situation.setMeLat(point[0]);
        situation.setMeLng(point[1]);
        situation.setRoute(null);
        situation.setNext(null);
        situation.setNextLat(null);
        situation.setNextLng(null);
        situation.setSlack(null);
        return true;
    }

    /**
     * 장소 이름 → {위도, 경도}. "○○구·○○동"이면 지역 가운데, 아니면 역·건물·가게 키워드 검색 첫 번째(내 위치 쪽).
     * 카카오 검색에 내 위치를 보내는 건 괜찮아요 (Gemini에는 안 보냄). 못 찾거나 키가 없으면 null
     */
    private double[] findPlacePoint(String place, double myLat, double myLng) {
        try {
            if (place.endsWith("구") || place.endsWith("동")) {
                List<FoundArea> areas = locationSearchClient.searchAreas(place);
                if (!areas.isEmpty()) {
                    return new double[] {areas.get(0).getLat(), areas.get(0).getLng()};
                }
            }
            List<FoundLocation> locations = locationSearchClient.searchLocations(place, myLat, myLng);
            if (!locations.isEmpty()) {
                return new double[] {locations.get(0).getLat(), locations.get(0).getLng()};
            }
        } catch (ApiException e) { // 카카오 키가 없거나 호출 실패
            log.warn("AI에게 묻기: 장소 이름 검색 실패 - {}", e.getMessage());
        }
        return null;
    }

    /** 지금 값이 있는 빈칸 (프롬프트에 "지금 쓸 수 있는 빈칸"으로 알려 줌) */
    private Set<String> findUsable(AskSituation situation) {
        Set<String> usable = new LinkedHashSet<>();
        if (situation.getRoute() != null) {
            usable.add(ROUTE);
        }
        usable.add(ME);
        if (situation.getNext() != null) {
            usable.add(NEXT);
        }
        if (situation.getSlack() != null) {
            usable.add(SLACK);
        }
        if (situation.getPrev() != null) {
            usable.add(PREV);
        }
        return usable;
    }

    // ---------------------------------------------------------------- 2·3. SQL 받기·검사

    /** Gemini에게 SQL 받기. 키가 없거나 실패하면 null */
    private SqlAnswer askSql(String message, String prevMessage, Set<String> usable, String retryReason) {
        if (!aiService.isReady()) {
            log.warn("AI에게 묻기 fallback: Gemini 키가 없어요");
            return null;
        }
        try {
            return aiService.askJsonQuick(sqlPromptMaker.makePrompt(message, prevMessage, usable, retryReason), SqlAnswer.class);
        } catch (ApiException e) {
            log.warn("AI에게 묻기 fallback: Gemini 호출 실패 - {}", e.getMessage());
            return null;
        }
    }

    /** SqlChecker 검사 + 지금 값이 없는 빈칸을 썼는지 (예: 대중교통 구간인데 :route) + 이어지는 질문인데 :prev를 빠뜨렸는지 */
    private SqlCheck checkAnswer(SqlAnswer answer, Set<String> usable) {
        SqlCheck check = sqlChecker.checkSql(answer.getSql(), hasMood(answer));
        if (!check.isOk()) {
            return check;
        }
        for (String name : check.getPlaceholders()) {
            if (!usable.contains(name)) {
                SqlCheck fail = new SqlCheck();
                fail.setOk(false);
                fail.setReason("지금 쓸 수 없는 빈칸 :" + name + " (지금 쓸 수 있는 빈칸만 써)");
                return fail;
            }
        }
        if (usable.contains(PREV) && !check.getPlaceholders().contains(PREV)) {
            SqlCheck fail = new SqlCheck();
            fail.setOk(false);
            fail.setReason("앞 결과 안에서 찾는 질문이야. WHERE에 p.id IN (:prev)를 꼭 넣어");
            return fail;
        }
        return check;
    }

    // ---------------------------------------------------------------- 4·5. 실행·후처리

    /** 실행 → (덜 돌아가는·가까운 순이면) 실제 걷는 길로 다시 줄 세움 → 영업·여유 → 원한 개수만. DB가 막으면(3초 초과·권한 밖) fallback */
    private PlaceAskResult runAndFinish(SqlAnswer answer, SqlCheck check, AskSituation situation) {
        List<AskedPlace> places;
        try {
            places = askSqlRunner.run(check, situation);
        } catch (DataAccessException e) { // DataAccessException: 스프링이 DB 오류를 감싸는 예외
            log.warn("AI에게 묻기 fallback: DB 실행 오류 - {} / SQL: {}", e.getMostSpecificCause().getMessage(), answer.getSql());
            return makeFallback();
        }
        log.info("AI에게 묻기 SQL: {} → {}곳", answer.getSql(), places.size());
        boolean isMoodMissed = false; // 분위기에 맞는 곳이 없어서 근처 곳을 대신 보여 주는지 (11/18)
        if (hasMood(answer)) {
            List<AskedPlace> moodPlaces = applyMood(places, answer);
            if (moodPlaces.isEmpty() && !places.isEmpty()) {
                isMoodMissed = true; // 빈 결과 대신 분위기 조건만 빼고, SQL로 찾은 근처 곳을 그대로 씀 (임베딩이 없는 동네 등)
            } else {
                places = moodPlaces;
            }
            if (check.getOrderBy() != null) {
                places = keepFirst(places, MOOD_WALK_POOL);
            }
        }
        if (check.getOrderBy() != null) {
            places = walkRanker.rank(places, situation, check.getOrderBy()); // 직선 → 실제 걷는 시간
        }
        List<AskedPlace> openPlaces = keepFirst(addOpenAndSlack(places, situation.getSlack()), check.getWantLimit());
        String band = null;
        if (check.getPlaceholders().contains(ROUTE)) {
            band = askSqlRunner.getRouteBand(situation.getRoute(), BAND_METERS);
        }
        PlaceAskResult result = makeResult(answer, situation.getSlack(), band, openPlaces);
        if (isMoodMissed && !openPlaces.isEmpty()) {
            result.setMessage("분위기에 꼭 맞는 곳은 못 찾아서, 근처 " + openPlaces.size() + "곳을 보여 드려요");
        }
        return result;
    }

    /** 분위기 조건(mood)이 있는 질문인지 */
    private boolean hasMood(SqlAnswer answer) {
        return answer.getMood() != null && !answer.getMood().isBlank();
    }

    /** 분위기 질문: 리뷰 RAG로 분위기에 맞는 곳만 그 순서로 남기고, 고른 이유·근거 리뷰를 붙임 */
    private List<AskedPlace> applyMood(List<AskedPlace> places, SqlAnswer answer) {
        List<String> placeIds = new ArrayList<>();
        for (AskedPlace place : places) {
            placeIds.add(place.getId());
        }
        List<MoodMatch> matches = reviewRagService.findByMood(placeIds, answer.getMood(), answer.getMoodWord());
        List<AskedPlace> moodPlaces = new ArrayList<>();
        for (MoodMatch match : matches) {
            AskedPlace place = findById(places, match.getPlaceId());
            if (place != null) {
                place.setMoodReason(match.getReason());
                place.setMoodEvidence(match.getQuote());
                moodPlaces.add(place);
            }
        }
        log.info("AI에게 묻기 분위기 '{}': 후보 {}곳 → {}곳", answer.getMood(), places.size(), moodPlaces.size());
        return moodPlaces;
    }

    private AskedPlace findById(List<AskedPlace> places, String id) {
        for (AskedPlace place : places) {
            if (place.getId().equals(id)) {
                return place;
            }
        }
        return null;
    }

    /** 보정 평점이 MIN_RATING(4.0)보다 낮은지. 평점을 모르면 빼지 않음 */
    static boolean isLowRated(AskedPlace place) {
        return place.getRating() != null && place.getRating() < MIN_RATING;
    }

    /** 지금 닫혀 있는 곳은 빼고, 닫는 시각·들르고 남는 여유를 채움 */
    private List<AskedPlace> addOpenAndSlack(List<AskedPlace> places, Integer slack) {
        List<AskedPlace> openPlaces = new ArrayList<>();
        for (AskedPlace place : places) {
            if (isLowRated(place)) {
                continue; // 평점 낮은 곳은 권하지 않음 (11/17: "제일 가까운 카페"로 3.43점 가게가 나왔어요. AI SQL이 가까운 순이면 평점 정렬이 빠짐)
            }
            OpenInfo open = placeService.getOpenInfo(toPlace(place), Geo.now().toLocalDate(), Geo.nowMinutes(), STAY_MINUTES);
            if (open != null && Boolean.FALSE.equals(open.getOpen())) {
                continue; // 휴무거나 닫을 시간
            }
            if (open != null) {
                place.setCloseAt(open.getCloseAt());
                place.setClosingSoon(open.getClosingSoon());
            }
            setStayAndSlack(place, getSlackAfter(slack, place.getDetourMinutes()));
            openPlaces.add(place);
        }
        return openPlaces;
    }

    /** 앞에서 count곳만 (count가 null이면 그대로). 다시 줄 세우느라 넉넉히 뽑은 후보를 사용자가 원한 개수로 */
    private List<AskedPlace> keepFirst(List<AskedPlace> places, Integer count) {
        if (count == null || places.size() <= count) {
            return places;
        }
        return new ArrayList<>(places.subList(0, count));
    }

    /**
     * 카드에 머무는 시간·여유. 카페는 [일정에 넣기]와 같은 CafeStay 계산 (다음 예약 5분 전 도착에 맞춰 머묾, 최대 60분)
     * → 화면 "N분 쉬었다 가면 돼요". 15분도 못 있으면 stayMinutes null (화면 "들르면 빠듯해요"). 카페 아닌 곳은 30분 기준 그대로
     */
    private void setStayAndSlack(AskedPlace place, Integer slackAfterBase) {
        if (!CafeStay.isCafe(place.getCategory()) || slackAfterBase == null) {
            place.setStayMinutes(STAY_MINUTES);
            place.setSlackAfter(slackAfterBase);
            return;
        }
        Integer stay = CafeStay.stayMinutes(slackAfterBase);
        place.setStayMinutes(stay);
        place.setSlackAfter(stay == null ? slackAfterBase : slackAfterBase + STAY_MINUTES - stay);
    }

    /** 들르고 남는 여유 = 지금 여유 − (돌아가는 분 + 머무는 분). 여유나 돌아가는 분을 모르면 null */
    private Integer getSlackAfter(Integer slack, Integer detourMinutes) {
        if (slack == null || detourMinutes == null) {
            return null;
        }
        return slack - detourMinutes - STAY_MINUTES;
    }

    // ---------------------------------------------------------------- 결과

    private PlaceAskResult makeResult(SqlAnswer answer, Integer slack, String band, List<AskedPlace> places) {
        PlaceAskResult result = new PlaceAskResult();
        result.setMessage(places.isEmpty() ? "조건에 맞는 곳을 찾지 못했어요. 조건을 바꿔서 물어봐 주세요." : places.size() + "곳을 찾았어요");
        result.setExplain(answer.getExplain());
        result.setSql(answer.getSql());
        result.setSlack(slack);
        result.setBand(band);
        result.setPlaces(places);
        return result;
    }

    /** 표에 없는 조건(가격·메뉴 등)이라 찾을 수 없음 → 이유만 안내 */
    /** 질문에 나온 곳을 지도에서 못 찾았을 때 (11/18) */
    private PlaceAskResult makePlaceNotFound(String place) {
        PlaceAskResult result = new PlaceAskResult();
        result.setMessage("'" + place + "'을(를) 지도에서 찾지 못했어요. 역이나 동네 이름으로 다시 물어봐 주세요.");
        return result;
    }

    private PlaceAskResult makeUnsupported(SqlAnswer answer) {
        PlaceAskResult result = new PlaceAskResult();
        result.setMessage(answer.getReason() == null ? "그 조건으로는 찾을 수 없어요." : answer.getReason());
        return result;
    }

    /** AI로 찾지 못함 → 부르는 쪽이 예전 반경 고르기로 */
    private PlaceAskResult makeFallback() {
        PlaceAskResult result = new PlaceAskResult();
        result.setFallback(true);
        result.setMessage("질문을 다 알아듣지 못해서, 범위를 골라 찾을게요.");
        return result;
    }

    // ---------------------------------------------------------------- 바꾸기 도우미

    private LocationRequest toLocation(double lat, double lng) {
        LocationRequest location = new LocationRequest();
        location.setLat(lat);
        location.setLng(lng);
        return location;
    }

    /** 영업 확인(getOpenInfo)에 넘길 Place (영업시간·휴무 원문만 있으면 됨) */
    private Place toPlace(AskedPlace askedPlace) {
        Place place = new Place();
        place.setId(askedPlace.getId());
        place.setName(askedPlace.getName());
        place.setLat(askedPlace.getLat());
        place.setLng(askedPlace.getLng());
        place.setUseTime(askedPlace.getUseTime());
        place.setRestDate(askedPlace.getRestDate());
        return place;
    }

    /** 앞 결과 id 목록 → "123,456" (빈 칸은 빼고 많아야 10개). 없으면 null = 이어지는 질문 아님 */
    private String toPrev(List<String> prevPlaceIds) {
        if (prevPlaceIds == null) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String id : prevPlaceIds) {
            if (id != null && !id.isBlank() && ids.size() < MAX_PREV_IDS) {
                ids.add(id.trim());
            }
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    /** 위도·경도 → 'SRID=4326;POINT(경도 위도)' (PostGIS는 경도가 먼저) */
    private String toPoint(double lat, double lng) {
        return "SRID=4326;POINT(" + lng + " " + lat + ")";
    }

    /** 길 좌표 [[위도, 경도], …] → 'SRID=4326;LINESTRING(경도 위도, …)' */
    private String toLine(List<double[]> coords) {
        StringBuilder line = new StringBuilder("SRID=4326;LINESTRING(");
        for (int i = 0; i < coords.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(coords.get(i)[1]).append(' ').append(coords.get(i)[0]);
        }
        return line.append(')').toString();
    }
}
