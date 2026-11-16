package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.CrowdHour;
import com.tripbiseo.plan.dto.PlaceCrowd;

/**
 * ③ 검증 줄의 붐빔 (11/13). crowd_cell·crowd_hour는 ② 찬기 표라 읽기(SELECT)만 해요 (찬기 OK, 찬기와 같은 쿼리).
 * SQL은 resources/mapper/plan/PlanCrowdMapper.xml
 */
@Mapper
public interface PlanCrowdMapper {

    /** 좌표가 든 250m 칸의 그 요일(1=월 … 7=일, 8=공휴일)·시각 평소 붐빔. 칸·시각 데이터가 없으면 null */
    CrowdHour selectCrowdHour(@Param("lat") double lat, @Param("lng") double lng, @Param("dayKind") int dayKind, @Param("hour") int hour);

    /** 장소들이 든 250m 칸의 그 요일들 낮(10~21시) 평균 붐빔 (11/16 "조용한" 점수). 칸이 없는 장소는 결과에 없음 */
    List<PlaceCrowd> selectDayCrowds(@Param("placeIds") List<String> placeIds, @Param("dayKinds") List<Integer> dayKinds);
}
