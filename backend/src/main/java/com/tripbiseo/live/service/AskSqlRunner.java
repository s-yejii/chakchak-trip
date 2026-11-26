package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.live.dto.AskSituation;
import com.tripbiseo.live.dto.AskedPlace;
import com.tripbiseo.live.dto.SqlCheck;
import com.tripbiseo.live.mapper.PlaceAskMapper;

import lombok.RequiredArgsConstructor;

/**
 * ④ AI에게 묻기: 검사를 통과한 AI SQL을 실행해요.
 * 묻기 전용 계정(ask_reader: 묻기용 뷰만 읽을 수 있음)으로 바꾸고, 쿼리 하나에 3초 제한을 걸어요.
 * PlaceAskServiceImpl 안에 두지 않은 이유:
 *   1. 같은 클래스 안에서 부르면 @Transactional이 동작하지 않아요
 *   2. Gemini를 기다리는 몇 초 동안 DB 연결을 잡고 있지 않으려고 DB 일만 따로 뺐어요
 */
@Component
@RequiredArgsConstructor
public class AskSqlRunner {

    private final PlaceAskMapper placeAskMapper;

    /** AI SQL 실행 → 찾은 장소 (AI SQL이 정한 순서). 3초가 넘거나 권한 밖이면 DB가 막고 예외가 남 */
    @Transactional(readOnly = true) // SET LOCAL은 이 트랜잭션 안에서만 유지, 끝나면 원래 계정으로
    public List<AskedPlace> run(SqlCheck check, AskSituation situation) {
        placeAskMapper.setRoleAskReader();
        placeAskMapper.setStatementTimeout();
        List<String> ids = placeAskMapper.selectPlaceIdsBySql(check.getSql(), situation.getRoute(),
                situation.getMe(), situation.getNext(), situation.getSlack(), situation.getPrev());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return sortLikeIds(placeAskMapper.selectAskedPlaceList(ids, situation.getRoute()), ids);
    }

    /** 지도용 경로 띠 GeoJSON (경로 양옆 meters m). 가는 길이 없으면 null */
    public String getRouteBand(String route, int meters) {
        if (route == null) {
            return null;
        }
        return placeAskMapper.selectRouteBand(route, meters);
    }

    /** 두 번째 쿼리(IN)는 순서를 안 지켜줘서 AI SQL이 정한 id 순서로 다시 맞춤 (같은 id가 두 번이면 한 번만) */
    private List<AskedPlace> sortLikeIds(List<AskedPlace> places, List<String> ids) {
        List<AskedPlace> sorted = new ArrayList<>();
        for (String id : ids) {
            for (AskedPlace place : places) {
                if (place.getId().equals(id) && !sorted.contains(place)) {
                    sorted.add(place);
                    break;
                }
            }
        }
        return sorted;
    }
}
