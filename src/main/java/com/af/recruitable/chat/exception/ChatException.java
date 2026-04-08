package com.af.recruitable.chat.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChatException extends RuntimeException {
    private final HttpStatus status;

    public ChatException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static ChatException notFound(String message) {
        return new ChatException(message, HttpStatus.NOT_FOUND);
    }

    public static ChatException forbidden(String message) {
        return new ChatException(message, HttpStatus.FORBIDDEN);
    }

    public static ChatException badRequest(String message) {
        return new ChatException(message, HttpStatus.BAD_REQUEST);
    }

    public static ChatException conflict(String message) {
        return new ChatException(message, HttpStatus.CONFLICT);
    }
}

