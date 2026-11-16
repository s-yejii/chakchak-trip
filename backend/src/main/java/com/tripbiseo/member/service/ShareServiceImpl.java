package com.tripbiseo.member.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.member.dto.PlaceImage;
import com.tripbiseo.member.dto.ShareLinkResponse;
import com.tripbiseo.member.dto.ShareTarget;
import com.tripbiseo.member.dto.SharedDay;
import com.tripbiseo.member.dto.SharedStop;
import com.tripbiseo.member.dto.SharedTrip;
import com.tripbiseo.member.mapper.ShareMapper;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final int CODE_BYTES = 16;        // 16바이트 → 22글자 코드
    private static final String SHARE_PATH = "/s/";  // 공유 화면 주소 앞부분
    private static final String SKIPPED = "skipped"; // 건너뛴 일정 칸은 안 보여 줌

    private final ShareMapper shareMapper;
    private final TripService tripService; // ② 여행 + 일정 칸 (읽기만)
    private final SecureRandom random = new SecureRandom(); // SecureRandom: 맞히기 어려운 무작위 값을 만드는 클래스

    /** 내 여행의 공유 링크 (이미 있으면 같은 링크) */
    @Override
    public ShareLinkResponse createShareLink(long memberId, long tripId) {
        tripService.getTrip(memberId, tripId); // 내 여행이 아니면 여기서 404
        String code = shareMapper.selectCodeByTrip(tripId);
        if (code == null) {
            code = makeCode();
            shareMapper.insertShare(code, tripId);
        }
        ShareLinkResponse response = new ShareLinkResponse();
        response.setCode(code);
        response.setPath(SHARE_PATH + code);
        return response;
    }

    /** 공유 코드로 일정 보기 (로그인 없이). 없는 코드면 404 */
    @Override
    public SharedTrip getSharedTrip(String code) {
        ShareTarget target = shareMapper.selectShareTarget(code);
        if (target == null) {
            throw fail(404, "공유가 끝났거나 없는 일정이에요.");
        }
        Trip trip = tripService.getTrip(target.getMemberId(), target.getTripId());
        SharedTrip shared = new SharedTrip();
        shared.setTitle(trip.getTitle());
        shared.setStartDate(trip.getStartDate());
        shared.setEndDate(trip.getEndDate());
        List<SharedStop> allStops = new ArrayList<>();
        for (Stop stop : trip.getStops()) {
            if (!SKIPPED.equals(stop.getState())) {
                SharedStop sharedStop = toSharedStop(stop);
                findDay(shared, stop.getDay()).getStops().add(sharedStop);
                allStops.add(sharedStop);
            }
        }
        addImages(allStops);
        return shared;
    }

    /** 장소 사진을 한 번에 읽어서 칸마다 넣기 (사진 API는 로그인이 필요해서 공유 응답에 같이 담음) */
    private void addImages(List<SharedStop> stops) {
        List<String> placeIds = new ArrayList<>();
        for (SharedStop stop : stops) {
            if (stop.getPlaceId() != null) {
                placeIds.add(stop.getPlaceId());
            }
        }
        if (placeIds.isEmpty()) {
            return;
        }
        Map<String, String> images = new HashMap<>();
        for (PlaceImage image : shareMapper.selectPlaceImageList(placeIds)) {
            images.put(image.getPlaceId(), image.getImageUrl());
        }
        for (SharedStop stop : stops) {
            stop.setImageUrl(images.get(stop.getPlaceId()));
        }
    }

    /** URL에 바로 쓸 수 있는 무작위 22글자 (Base64 URL 방식, = 없이) */
    private String makeCode() {
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 그 날짜 묶음을 찾고, 없으면 새로 만들어 붙임 (일정 칸이 day 순으로 와서 마지막 묶음만 보면 됨) */
    private SharedDay findDay(SharedTrip shared, Integer day) {
        int dayNumber = day == null ? 1 : day;
        int size = shared.getDays().size();
        if (size > 0 && shared.getDays().get(size - 1).getDay() == dayNumber) {
            return shared.getDays().get(size - 1);
        }
        SharedDay sharedDay = new SharedDay();
        sharedDay.setDay(dayNumber);
        shared.getDays().add(sharedDay);
        return sharedDay;
    }

    /** 일정 칸에서 보여 줄 것만 옮겨 담기 (메모·도착 기록은 뺌) */
    private SharedStop toSharedStop(Stop stop) {
        SharedStop shared = new SharedStop();
        shared.setTime(stop.getTime());
        shared.setName(stop.getName());
        shared.setCategory(stop.getCategory());
        shared.setStay(stop.getStay());
        shared.setLat(stop.getLat());
        shared.setLng(stop.getLng());
        shared.setPlaceId(stop.getPlaceId());
        return shared;
    }
}
