package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.HangangBusPier;
import com.tripbiseo.plan.dto.HangangBusTime;

/**
 * ③ 한강버스 추천 (11/16). 선착장(retrip_place)·시간표(hangang_bus_time)는 ① 상재 표라 읽기(SELECT)만 해요.
 * SQL은 resources/mapper/plan/HangangBusMapper.xml
 */
@Mapper
public interface HangangBusMapper {

    /** 좌표에서 meters 안 한강버스 선착장, 가까운 순으로 limit개 */
    List<HangangBusPier> selectPierList(@Param("lat") double lat, @Param("lng") double lng, @Param("meters") int meters, @Param("limit") int limit);

    /** 한강버스 시간표 전체 (하루 100줄 남짓이라 한 번에 읽음) */
    List<HangangBusTime> selectTimeList();
}
