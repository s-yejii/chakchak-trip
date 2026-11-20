package com.tripbiseo.plan.dto;

import lombok.Data;

/** ③ 하루 판단용 장소 한 곳 + PostGIS가 정한 묶음 번호 (DayCheckMapper) */
@Data
public class DayCheckPlace {
    private String placeId;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private Integer clusterNo; // ST_ClusterDBSCAN 묶음 번호 (0부터)
}
