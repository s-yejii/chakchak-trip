package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.ChatIntent;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.PlaceAskResult;
import com.tripbiseo.live.dto.PlaceTagCount;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.live.dto.SkipRequest;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 AI 채팅 → 근처 추천 → 일정에 넣기. 대안은 AlternativeServiceImpl, 지금 상황은 StatusServiceImpl.
 * AI 원칙: 규칙 먼저, 애매할 때만 Gemini(분류·답 문장만). 추천·시각 계산은 코드로. 좌표는 프롬프트에 넣지 않음.
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MIN_RADIUS_M = 100;
    private static final int MAX_RADIUS_M = 3000;
    private static final int RECOMMEND_COUNT = 3;
    private static final int ON_ROUTE_COUNT = 5;          // 비슷한 가게 [다음 고정 일정 가는 길]: 5곳까지 (11/17 ③, ④ 예지 OK)
    private static final double TRUSTED_RATING = 4.9;     // 가는 길: 평점 이 이상 + 리뷰 이 개수 이상이면 "검증된 가게" → 덜 돌아가는 순으로 맨 앞
    private static final int TRUSTED_REVIEWS = 1000;
    private static final String HHMM = "([01]\\d|2[0-3]):[0-5]\\d"; // 예약 시각 15:00 (11/16 ③)
    private static final int CAFE_STAY_MIN = CafeStay.BASE_MIN; // 일정에 넣기: 카페·디저트·베이커리 30분(여유가 많으면 CafeStay 규칙으로 늘림), 아니면 60분 (API.md, 11/17)
    private static final int OTHER_STAY_MIN = 60;
    private static final double WALK_METERS_PER_MIN = 75; // 도착 예상: 걸어서 분당 75m, 길 돌아감 1.3배
    private static final double ROAD_FACTOR = 1.3;
    private static final String FOOD_WORDS = ".*(식당|맛집|음식|밥|한식|중식|일식|양식|고기|분식).*";
    private static final String NOT_FOOD_WORDS = ".*(카페|디저트|베이커리|숙박|관광|문화|공원|쇼핑).*";
    private static final String DRINK_WORDS = ".*(주점|이자카야|호프|맥주|와인|포차|칵테일).*";
    // 비슷한 가게: 네이버 업종 이름이 달라도 같은 일식으로 봄 (스시도 "일식당" ↔ 스시이세이 "초밥,롤", 11/16 ③ 요청)
    private static final Set<String> JAPANESE_TYPES = Set.of("일식", "일식당", "초밥", "롤", "스시", "오마카세");
    private static final String JAPANESE_GROUP = "일식";

    private final TripService tripService;
    private final PlaceService placeService;
    private final StatusService statusService;             // 질문 답할 때 오늘 상황 (RAG의 근거)
    private final AlternativeService alternativeService;   // "일정 바꿔줘" → 대안, 일정에 넣기 → 대안 적용 재사용
    private final LiveTripReader liveTripReader;           // 공통 오류 검사 + 진행 중인 여행
    private final LiveStopFinder liveStopFinder;
    private final AlternativeCalculator alternativeCalculator;
    private final LiveChatClassifier liveChatClassifier;   // 말 분류 (규칙 → Gemini)
    private final LiveChatAnswerer liveChatAnswerer;       // 질문 답 (템플릿 → Gemini RAG)
    private final PlaceTagReader placeTagReader;           // 리뷰 태그 점수·이유 칩
    private final PlaceAskService placeAskService;         // 추천 질문 → SQL로 바꿔 찾기 (Text-to-SQL, 11/08)

    /** 여행 중 채팅: 말 분류 → 추천이면 질문으로 바로 찾기(못 찾으면 반경 묻기), 바꾸기면 대안, 질문이면 오늘 상황으로 답 */
    @Override
    public AssistResponse assist(long memberId, long tripId, AssistRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        }
        liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        AssistResponse response = new AssistResponse();
        // 이어지는 질문("이 중에 …", 11/13): 앞 결과 안에서 다시 찾음. 아니면 앞 결과는 안 씀
        if (liveChatClassifier.isFollowUp(message, request.getPrevPlaceIds())) {
            ChatIntent intent = liveChatClassifier.classifyFollowUp(message, request.getPrevMessage());
            answerRecommend(response, memberId, tripId, request, message, intent, true);
            return response;
        }
        ChatIntent intent = liveChatClassifier.classify(message);
        if ("recommend".equals(intent.getKind())) {
            answerRecommend(response, memberId, tripId, request, message, intent, false);
        } else if ("change".equals(intent.getKind())) {
            response.setKind("alternatives");
            response.setData(alternativeService.makeAlternatives(memberId, tripId, toAlternativesRequest(request, message)));
        } else {
            response.setKind("text");
            response.setText(liveChatAnswerer.answer(message, statusService.getStatus(memberId, tripId, toLocation(request))));
        }
        return response;
    }

    /**
     * 반경 안 추천 3곳: PostGIS 반경 검색 → 평점 없는 곳 빼기 → 점수(보정 평점 + 원하는 리뷰 태그) 순
     * → 도착할 때 닫힌 곳 빼기(closedSkipped) → 영업 정보 + 이유 칩
     */
    @Override
    public RecommendResponse recommend(long memberId, long tripId, RecommendRequest request) {
        if (request.getRadius() == null) {
            throw fail(400, "radius 값이 필요해요.");
        }
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        if (request.getRadius() < MIN_RADIUS_M || request.getRadius() > MAX_RADIUS_M) {
            throw fail(400, "반경은 100m~3km예요.");
        }
        List<Place> candidates = findCandidates(request);
        int stay = getStay(request.getCategory());
        Place missed = null;
        if (request.getSimilarTo() != null) {
            // 11/12: 놓친 예약과 비슷한 가게 (업종이 겹치는 곳만, 머무는 시간은 놓친 칸 그대로)
            missed = placeService.getPlace(request.getSimilarTo()); // 없으면 404
            if (Boolean.TRUE.equals(request.getOnRoute())) {
                candidates = findSameTypePlaces(request, missed); // 가는 길은 반경이 넓어서 가까운 200곳에 안 들어가는 가게가 생김 (11/17)
            }
            candidates = keepSimilarPlaces(candidates, missed, trip);
            stay = getMissedStay(trip, missed, stay);
        }
        Map<String, PlaceTagCount> tagMap = placeTagReader.countTags(candidates);
        List<String> wantedTags = request.getTags() == null ? new ArrayList<>() : request.getTags();
        sortByScore(candidates, tagMap, wantedTags);
        if (missed != null) {
            moveSameBrandFirst(candidates, missed);
        }
        boolean isOnRoute = missed != null && Boolean.TRUE.equals(request.getOnRoute()) && request.getToLat() != null && request.getToLng() != null;
        if (!isOnRoute) {
            return pickOpenPlaces(candidates, tagMap, wantedTags, stay, RECOMMEND_COUNT);
        }
        Map<String, Integer> detours = getDetours(candidates, missed, request.getToLat(), request.getToLng());
        sortOnRoute(candidates, detours);
        RecommendResponse response = pickOpenPlaces(candidates, tagMap, wantedTags, stay, ON_ROUTE_COUNT);
        for (RecommendPlace place : response.getPlaces()) {
            place.setDetour(detours.get(place.getId()));
        }
        return response;
    }

    /**
     * 추천 장소를 바로 다음 일정으로. 새 칸을 맨 앞에 둔 "대안"을 계산해서 대안 적용(applyAlternative)으로 저장해요.
     * 고정 일정에 늦으면 고정 일정 바로 앞 칸 머무는 시간을 최대 절반까지 줄임 (API.md)
     * replaceStopId가 있으면 맨 앞 대신 그 칸 자리에 넣고, 그 칸은 skipped (놓친 예약 바꾸기, 11/12)
     * afterStopId가 있으면 그 칸 다음에 넣음 (사용자가 넣을 자리를 고름, 11/13)
     */
    @Override
    @Transactional
    public InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        Place place = placeService.getPlace(request.getPlaceId()); // 없으면 404 "장소를 찾을 수 없어요."
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        if (isPlanned(todayStops, current, place.getId())) {
            throw fail(400, "이미 일정에 있는 곳이에요."); // 같은 곳을 두 번 넣지 않게 (11/13)
        }

        if (request.getReplaceStopId() != null) {
            findRemainingStop(todayStops, current, request.getReplaceStopId(), "바꿀 일정을 찾을 수 없어요."); // 남은 칸이 아니면 400
        }
        Stop after = null;
        if (request.getAfterStopId() != null) {
            after = findRemainingStop(todayStops, current, request.getAfterStopId(), "넣을 자리를 찾을 수 없어요."); // 남은 칸이 아니면 400
        }
        Stop newStop = toNewStop(place, liveStopFinder.getToday(trip));
        setReservedTime(newStop, request.getReservedTime()); // 예약했으면 고정 일정으로 (11/16)
        List<Stop> plan = makeInsertPlan(todayStops, current, newStop, request.getReplaceStopId(), request.getAfterStopId());
        plan = alternativeCalculator.copyStops(plan); // 원본을 고치지 않게 복사본으로 계산
        Alternative result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
        if (CafeStay.fitNewCafe(plan, liveStopFinder.findFixedStop(plan, null), result.getSlackMin())) { // 카페는 다음 예약 5분 전 도착에 맞춰 머묾 (11/17, AI 카드와 같은 계산)
            result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
        }
        String shortened = shortenBeforeFixed(plan, result);
        if (shortened != null) {
            result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
        }
        alternativeService.applyAlternative(memberId, tripId, toApplyRequest(result, request.getLat(), request.getLng(), "insert"));

        InsertResponse response = new InsertResponse();
        response.setTrip(tripService.getTrip(memberId, tripId));
        response.setMessage(getInsertMessage(place, after, plan, result, shortened));
        return response;
    }

    /** 못 가게 된 일정 하나를 빼고, 남은 일정을 앞으로 당겨 다시 계산 (11/12 놓친 예약) */
    @Override
    @Transactional // 칸 빼기(skipped) + 남은 칸 시각 다시 저장. 하나라도 실패하면 전부 되돌림
    public InsertResponse skipStop(long memberId, long tripId, SkipRequest request) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        Stop skipped = findRemainingStop(todayStops, current, request.getStopId(), "바꿀 일정을 찾을 수 없어요."); // 없으면 400

        List<Stop> plan = new ArrayList<>();
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current) && stop != skipped) {
                plan.add(stop);
            }
        }
        if (plan.isEmpty()) {
            tripService.skipStops(memberId, tripId, List.of(skipped.getId())); // 뒤에 남은 칸이 없으면 빼기만
        } else {
            // plan에 없는 칸(뺀 칸)은 applyAlternative가 skipped로 바꿈
            plan = alternativeCalculator.copyStops(plan);
            Alternative result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
            alternativeService.applyAlternative(memberId, tripId, toApplyRequest(result, request.getLat(), request.getLng(), "skip"));
        }
        InsertResponse response = new InsertResponse();
        response.setTrip(tripService.getTrip(memberId, tripId));
        response.setMessage(skipped.getName() + "을(를) 빼고 남은 일정을 다시 계산했어요.");
        return response;
    }

    // ---------------------------------------------------------------- 채팅 도우미

    /** 추천 질문: Text-to-SQL로 바로 찾기 (places). AI로 못 찾으면 예전처럼 반경 묻기 (ask-radius). isFollowUp이면 앞 결과 안에서 */
    private void answerRecommend(AssistResponse response, long memberId, long tripId, AssistRequest request,
                                 String message, ChatIntent intent, boolean isFollowUp) {
        String prevMessage = isFollowUp ? request.getPrevMessage() : null;
        List<String> prevPlaceIds = isFollowUp ? request.getPrevPlaceIds() : null;
        PlaceAskResult found = placeAskService.findPlaces(memberId, tripId, message, prevMessage, prevPlaceIds,
                request.getLat(), request.getLng());
        if (found.isFallback()) {
            response.setKind("ask-radius");
            response.setCategory(intent.getCategory());
            response.setTags(intent.getTags());
            return;
        }
        response.setKind("places");
        response.setFound(found);
    }

    private AlternativesRequest toAlternativesRequest(AssistRequest request, String message) {
        AlternativesRequest alternativesRequest = new AlternativesRequest();
        alternativesRequest.setLat(request.getLat());
        alternativesRequest.setLng(request.getLng());
        alternativesRequest.setReason("request");
        alternativesRequest.setRequest(message);
        return alternativesRequest;
    }

    private LocationRequest toLocation(AssistRequest request) {
        LocationRequest location = new LocationRequest();
        location.setLat(request.getLat());
        location.setLng(request.getLng());
        return location;
    }

    // ---------------------------------------------------------------- 추천 도우미

    /** 반경 안 후보 (평점 있는 곳만). 식당·술집은 분류 말이 여러 가지라 SQL은 전체로 찾고 여기서 거름 */
    private List<Place> findCandidates(RecommendRequest request) {
        String category = request.getCategory() == null ? "" : request.getCategory().trim();
        boolean isFood = category.matches(FOOD_WORDS);
        boolean isDrink = category.contains("술");
        String sqlCategory = isFood || isDrink || category.isEmpty() ? null : category;
        List<Place> candidates = new ArrayList<>();
        for (Place place : placeService.getNearbyPlaceList(request.getLat(), request.getLng(), request.getRadius(), sqlCategory)) {
            if (place.getRating() == null) {
                continue;
            }
            String placeCategory = place.getCategory() == null ? "" : place.getCategory();
            if (isFood && placeCategory.matches(NOT_FOOD_WORDS)) {
                continue;
            }
            if (isDrink && !placeCategory.matches(DRINK_WORDS)) {
                continue;
            }
            candidates.add(place);
        }
        return candidates;
    }

    /** 점수 높은 순 정렬 (sort에 넘기는 규칙: 두 장소의 점수를 거꾸로 비교해서 큰 게 앞) */
    private void sortByScore(List<Place> places, Map<String, PlaceTagCount> tagMap, List<String> wantedTags) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (Place place : places) {
            scoreMap.put(place.getId(), placeTagReader.getScore(place, tagMap.get(place.getId()), wantedTags));
        }
        places.sort((first, second) -> Double.compare(scoreMap.get(second.getId()), scoreMap.get(first.getId())));
    }

    /** 놓친 가게와 같은 업종 낱말로 반경 안을 찾기 (가는 길 모드, 11/17). 평점 없는 곳은 빼고 같은 곳은 한 번만 */
    private List<Place> findSameTypePlaces(RecommendRequest request, Place missed) {
        Set<String> words = new HashSet<>();
        for (String word : (missed.getCategory() == null ? "" : missed.getCategory()).split(",")) {
            if (!word.isBlank()) {
                words.add(word.trim());
            }
        }
        for (String word : new ArrayList<>(words)) {
            if (JAPANESE_TYPES.contains(word)) {
                words.addAll(JAPANESE_TYPES); // 일식당·초밥 등은 한 무리로 봐요 (splitTypes와 같음)
            }
        }
        Map<String, Place> found = new HashMap<>();
        for (String word : words) {
            for (Place place : placeService.getNearbyPlaceList(request.getLat(), request.getLng(), request.getRadius(), word)) {
                if (place.getRating() != null) {
                    found.put(place.getId(), place);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** 가게마다 더 도는 거리(m): 놓친 가게 → 가게 → 다음 고정 일정 − 놓친 가게 → 다음 고정 일정 (손에 있는 점이라 Geo로) */
    private Map<String, Integer> getDetours(List<Place> places, Place missed, double toLat, double toLng) {
        double direct = Geo.distanceMeters(missed.getLat(), missed.getLng(), toLat, toLng);
        Map<String, Integer> detours = new HashMap<>();
        for (Place place : places) {
            double via = Geo.distanceMeters(missed.getLat(), missed.getLng(), place.getLat(), place.getLng())
                    + Geo.distanceMeters(place.getLat(), place.getLng(), toLat, toLng);
            detours.put(place.getId(), (int) Math.round(via - direct));
        }
        return detours;
    }

    /**
     * 가는 길 정렬 (11/17 주형): 1) 검증된 가게(평점 4.9 이상 + 리뷰 1000개 이상)를 덜 돌아가는 순으로 맨 앞
     * 2) 나머지는 평점 × log(리뷰 수) 큰 순 (평점이 같으면 리뷰 많은 곳, 리뷰가 몇 개뿐인 5점은 뒤로)
     */
    static void sortOnRoute(List<Place> places, Map<String, Integer> detours) {
        places.sort((first, second) -> {
            boolean isFirstTrusted = isTrusted(first);
            if (isFirstTrusted != isTrusted(second)) {
                return isFirstTrusted ? -1 : 1;
            }
            if (isFirstTrusted) {
                return Integer.compare(detours.get(first.getId()), detours.get(second.getId()));
            }
            return Double.compare(popularScore(second), popularScore(first));
        });
    }

    private static boolean isTrusted(Place place) {
        return place.getRating() != null && place.getRating() >= TRUSTED_RATING
                && place.getReviewCount() != null && place.getReviewCount() >= TRUSTED_REVIEWS;
    }

    // log(리뷰 수): 리뷰가 10개 → 100개 → 1000개로 늘 때 점수가 같은 만큼씩 올라요 (리뷰 많은 곳이 너무 앞서지 않게)
    private static double popularScore(Place place) {
        double rating = place.getRating() == null ? 0 : place.getRating();
        int reviews = place.getReviewCount() == null ? 0 : place.getReviewCount();
        return rating * Math.log(reviews + 1);
    }

    /** 앞에서부터 보면서 도착할 때 열려 있는 곳 count개. 닫힌 곳은 세서 closedSkipped */
    private RecommendResponse pickOpenPlaces(List<Place> places, Map<String, PlaceTagCount> tagMap, List<String> wantedTags, int stay, int count) {
        RecommendResponse response = new RecommendResponse();
        LocalDate today = Geo.now().toLocalDate();
        int closedSkipped = 0;
        for (Place place : places) {
            int walkMinutes = (int) Math.ceil(place.getDistance() * ROAD_FACTOR / WALK_METERS_PER_MIN);
            OpenInfo open = placeService.getOpenInfo(place, today, Geo.nowMinutes() + walkMinutes, stay);
            if (Boolean.FALSE.equals(open.getOpen())) {
                closedSkipped++;
                continue;
            }
            response.getPlaces().add(toRecommendPlace(place, open, placeTagReader.getReasons(tagMap.get(place.getId()), wantedTags)));
            if (response.getPlaces().size() == count) {
                break;
            }
        }
        response.setClosedSkipped(closedSkipped);
        return response;
    }

    /** 장소 카드 + 영업 정보 + 이유 칩 */
    private RecommendPlace toRecommendPlace(Place place, OpenInfo open, List<String> reasons) {
        RecommendPlace recommend = new RecommendPlace();
        recommend.setId(place.getId());
        recommend.setName(place.getName());
        recommend.setCategory(place.getCategory());
        recommend.setAddress(place.getAddress());
        recommend.setLat(place.getLat());
        recommend.setLng(place.getLng());
        recommend.setRating(place.getRating());
        recommend.setReviewCount(place.getReviewCount());
        recommend.setNaverUrl(placeService.getPlaceCard(place, null, null).getNaverUrl());
        recommend.setDistance((int) Math.round(place.getDistance()));
        recommend.setOpen(open.getOpen());
        recommend.setCloseAt(open.getCloseAt());
        recommend.setMinutesLeft(open.getMinutesLeft());
        recommend.setClosingSoon(Boolean.TRUE.equals(open.getClosingSoon()));
        recommend.setReasons(reasons);
        return recommend;
    }

    /** 카페·디저트·베이커리면 30분(넣을 때 다음 예약에 맞춰 CafeStay.fitNewCafe가 바꿈), 아니면 60분 */
    private int getStay(String category) {
        return CafeStay.isCafe(category) ? CAFE_STAY_MIN : OTHER_STAY_MIN;
    }

    // ---------------------------------------------------------------- 일정에 넣기 도우미

    /** 추천 장소로 만든 새 칸 (id 없음 → 대안 적용이 createStop으로 저장) */
    private Stop toNewStop(Place place, int today) {
        Stop stop = new Stop();
        stop.setDay(today);
        stop.setPlaceId(place.getId());
        stop.setName(place.getName());
        stop.setCategory(place.getCategory());
        stop.setAddress(place.getAddress());
        stop.setLat(place.getLat());
        stop.setLng(place.getLng());
        stop.setStay(getStay(place.getCategory()));
        stop.setFixed(false);
        stop.setState("todo");
        return stop;
    }

    /** 예약 시각을 받았으면 새 칸을 고정 일정 + 그 시각으로 (예: 놓친 예약 대신 전화로 15:00에 잡은 가게). 형식이 틀리면 400 */
    static void setReservedTime(Stop newStop, String reservedTime) {
        if (reservedTime == null || reservedTime.isBlank()) {
            return; // 예약 안 함: 지금처럼 고정 아닌 칸
        }
        if (!reservedTime.matches(HHMM)) {
            throw fail(400, "예약 시각은 15:00처럼 보내 주세요.");
        }
        newStop.setFixed(true);
        newStop.setTime(reservedTime);
    }

    /** 고정 일정에 늦으면 그 바로 앞 칸(고정 아님)의 머묾을 최대 절반까지 줄임. 줄였으면 그 칸 이름, 아니면 null */
    private String shortenBeforeFixed(List<Stop> plan, Alternative result) {
        Stop fixedStop = liveStopFinder.findFixedStop(plan, null);
        if (fixedStop == null || result.getSlackMin() >= 0) {
            return null;
        }
        int fixedIndex = plan.indexOf(fixedStop);
        if (fixedIndex < 1 || Boolean.TRUE.equals(plan.get(fixedIndex - 1).getFixed())) {
            return null;
        }
        Stop before = plan.get(fixedIndex - 1);
        int stay = liveStopFinder.getStay(before);
        int cut = Math.min(-result.getSlackMin(), stay / 2);
        if (cut <= 0) {
            return null;
        }
        before.setStay(stay - cut);
        return before.getName();
    }

    private ApplyRequest toApplyRequest(Alternative result, Double lat, Double lng, String key) {
        ApplyRequest apply = new ApplyRequest();
        apply.setKey(key);
        apply.setStops(result.getStops());
        apply.setLat(lat);
        apply.setLng(lng);
        return apply;
    }

    // ---------------------------------------------------------------- 놓친 예약 도우미 (11/12)

    /**
     * 넣을 일정: replaceStopId가 있으면 그 칸 자리에 (그 칸은 빠져서 skipped가 됨),
     * afterStopId가 있으면 그 칸 바로 뒤에, 둘 다 없으면 맨 앞에
     */
    private List<Stop> makeInsertPlan(List<Stop> todayStops, Stop current, Stop newStop, Long replaceStopId, Long afterStopId) {
        List<Stop> plan = new ArrayList<>();
        if (replaceStopId == null && afterStopId == null) {
            plan.add(newStop);
        }
        for (Stop stop : todayStops) {
            if (!liveStopFinder.isRemaining(stop, current)) {
                continue;
            }
            plan.add(stop.getId().equals(replaceStopId) ? newStop : stop);
            if (stop.getId().equals(afterStopId)) {
                plan.add(newStop);
            }
        }
        return plan;
    }

    /** 이 장소가 지금 있는 칸이거나 남은 칸에 이미 있는지 (다녀온 곳은 다시 넣어도 됨) */
    private boolean isPlanned(List<Stop> todayStops, Stop current, String placeId) {
        for (Stop stop : todayStops) {
            boolean isAhead = stop == current || liveStopFinder.isRemaining(stop, current);
            if (isAhead && placeId.equals(stop.getPlaceId())) {
                return true;
            }
        }
        return false;
    }

    /** 남은 칸 중 id가 같은 칸. 없으면 400 (message) */
    private Stop findRemainingStop(List<Stop> todayStops, Stop current, Long stopId, String message) {
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current) && stop.getId().equals(stopId)) {
                return stop;
            }
        }
        throw fail(400, message);
    }

    /** 놓친 가게와 업종 낱말이 하나라도 겹치는 곳만. 이미 일정에 있는 곳(놓친 가게 포함)은 뺌 */
    private List<Place> keepSimilarPlaces(List<Place> candidates, Place missed, Trip trip) {
        Set<String> missedTypes = splitTypes(missed.getCategory());
        Set<String> inTripIds = new HashSet<>();
        for (Stop stop : trip.getStops()) {
            if (stop.getPlaceId() != null) {
                inTripIds.add(stop.getPlaceId());
            }
        }
        List<Place> similar = new ArrayList<>();
        for (Place place : candidates) {
            if (!inTripIds.contains(place.getId()) && hasCommonType(missedTypes, place.getCategory())) {
                similar.add(place);
            }
        }
        return similar;
    }

    /** biz_type("일식,초밥")을 낱말 묶음으로. 같은 종류의 다른 이름은 한 이름으로 (일식당·초밥·롤 → 일식) */
    private Set<String> splitTypes(String bizType) {
        Set<String> types = new HashSet<>();
        if (bizType == null) {
            return types;
        }
        for (String word : bizType.split(",")) {
            if (word.isBlank()) {
                continue;
            }
            String type = word.trim();
            types.add(JAPANESE_TYPES.contains(type) ? JAPANESE_GROUP : type);
        }
        return types;
    }

    /** 낱말 묶음과 이 업종이 하나라도 겹치는지 */
    private boolean hasCommonType(Set<String> types, String bizType) {
        for (String word : splitTypes(bizType)) {
            if (types.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 같은 브랜드 다른 지점을 맨 앞으로 ("스시하루 강남점" → "스시하루"). 나머지 순서는 그대로 */
    private void moveSameBrandFirst(List<Place> places, Place missed) {
        String brand = brandOf(missed.getName());
        if (brand == null) {
            return;
        }
        List<Place> sameBrand = new ArrayList<>();
        List<Place> others = new ArrayList<>();
        for (Place place : places) {
            if (brand.equals(brandOf(place.getName()))) {
                sameBrand.add(place);
            } else {
                others.add(place);
            }
        }
        places.clear();
        places.addAll(sameBrand);
        places.addAll(others);
    }

    /** 이름 끝 "○○점"을 뗀 브랜드 이름. 띄어쓰기가 없거나 "점"으로 안 끝나면 null (브랜드로 안 봄) */
    private String brandOf(String name) {
        int space = name == null ? -1 : name.lastIndexOf(' ');
        if (space < 0 || !name.endsWith("점")) {
            return null;
        }
        return name.substring(0, space);
    }

    /** 놓친 칸에서 머물려던 시간 (일정에서 못 찾으면 기본값) */
    private int getMissedStay(Trip trip, Place missed, int defaultStay) {
        for (Stop stop : trip.getStops()) {
            if (missed.getId().equals(stop.getPlaceId()) && stop.getStay() != null) {
                return stop.getStay();
            }
        }
        return defaultStay;
    }

    /** "카페OO을(를) 다음 일정으로 넣었어요. (청계천 시간을 줄여) 세종문화회관에는 12분 여유가 있어요." */
    private String getInsertMessage(Place place, Stop after, List<Stop> plan, Alternative result, String shortened) {
        String where = after == null ? "다음 일정으로" : after.getName() + " 다음에";
        String message = place.getName() + "을(를) " + where + " 넣었어요. ";
        if (shortened != null) {
            message += shortened + " 시간을 줄여 ";
        }
        Stop fixedStop = liveStopFinder.findFixedStop(plan, null);
        if (fixedStop == null) {
            return message + "이후 일정 시각을 다시 계산했어요.";
        }
        if (result.getSlackMin() >= 0) {
            return message + fixedStop.getName() + "에는 " + Geo.toDuration(result.getSlackMin()) + " 여유가 있어요.";
        }
        return message + "그래도 " + fixedStop.getName() + "에 " + Geo.toDuration(-result.getSlackMin()) + " 늦어요. '대안 보기'를 확인하세요.";
    }
}
