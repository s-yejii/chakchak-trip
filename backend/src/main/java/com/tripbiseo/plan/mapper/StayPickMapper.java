package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.StayPick;

/** ③ 근처 추천 숙소. retrip_place는 공통 테이블이라 읽기(SELECT)만 해요. SQL은 resources/mapper/plan/StayPickMapper.xml */
@Mapper
public interface StayPickMapper {

    /** 기준점 반경 안 숙박: 사진 있는 호텔 먼저, 그 안에서 가까운 순 */
    List<StayPick> selectStayList(@Param("lat") double lat, @Param("lng") double lng,
                                  @Param("radius") int radius, @Param("limit") int limit);

    /** 숙박 한 곳 (시연 숙소를 맨 위에 둘 때), 없으면 null */
    StayPick selectStay(@Param("placeId") String placeId, @Param("lat") double lat, @Param("lng") double lng);
}
