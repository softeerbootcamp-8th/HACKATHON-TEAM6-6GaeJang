package com.delipot.chat.dto;

/** STOMP 전용 에러 프레임. 클라이언트는 /user/queue/errors 를 구독해서 받는다. */
public record ChatErrorMessage(String code, String message) {
}
