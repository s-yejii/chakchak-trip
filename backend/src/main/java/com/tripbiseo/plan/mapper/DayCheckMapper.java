package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.DayCheckPlace;

/** ③ 하루 판단: 담은 장소를 PostGIS로 동네(1.5km)끼리 묶기. retrip_place는 읽기만. SQL은 resources/mapper/plan/DayCheckMapper.xml */
@Mapper
public interface DayCheckMapper {

    /** 장소들과 묶음 번호 (같은 번호 = 서로 eps 안으로 이어진 동네) */
    List<DayCheckPlace> selectPlaceClusterList(@Param("placeIds") List<String> placeIds, @Param("eps") int eps);
}
