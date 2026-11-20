package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.DayCheckRequest;
import com.tripbiseo.plan.dto.DayCheckResponse;

/** ③ 담은 장소를 하루에 다 갈 수 있는지 (11/20) */
public interface DayCheckService {

    /** 묶음 수·필요한 분·하루를 넘는지 (시연 계정은 늘 안 넘는 걸로) */
    DayCheckResponse checkDay(long memberId, DayCheckRequest request);
}
