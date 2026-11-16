package com.tripbiseo.common.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 모든 오류를 {message} 한 모양으로. 화면은 message를 그대로 보여 줍니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        if (e.getStatus() >= 500) log.warn("[{}] {}", e.getStatus(), e.getMessage());
        return body(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
        return body(400, "요청 형식이 올바르지 않아요.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException e) {
        return body(400, e.getParameterName() + " 값이 필요해요.");
    }

    /**
     * 올린 파일이 spring.servlet.multipart.max-file-size(100MB, ⑤ 사진·영상 앨범에 맞춤)보다 큼 → 500이 아니라 400
     * ③ 예약 캡처의 5MB 제한은 CaptureServiceImpl이 따로 검사해서 "사진은 5MB까지예요."로 답함 (100MB 안쪽이라 여기까지 안 옴)
     * 11/16 ⑤: 문구가 5MB로 남아 있어 100MB 넘는 영상에도 "5MB까지"로 나오던 것을 실제 한도로 고침
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(400, "파일은 100MB까지예요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unknown(Exception e) {
        log.error("처리하지 못한 오류", e);
        return body(500, "서버 오류가 났어요. 잠시 후 다시 시도해 주세요.");
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
