package com.tripbiseo.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.member.dto.PlaceImage;
import com.tripbiseo.member.dto.ShareTarget;

/** ① 일정 공유 링크 (DB trip_share) */
@Mapper
public interface ShareMapper {

    /** 여행에 이미 만든 공유 코드 (없으면 null) */
    String selectCodeByTrip(@Param("tripId") long tripId);

    /** 공유 코드 → 여행 번호와 주인 (없으면 null) */
    ShareTarget selectShareTarget(@Param("code") String code);

    /** 장소들의 대표 사진 (retrip_place 읽기만) */
    List<PlaceImage> selectPlaceImageList(@Param("placeIds") List<String> placeIds);

    /** 공유 코드 저장 */
    void insertShare(@Param("code") String code, @Param("tripId") long tripId);
}
