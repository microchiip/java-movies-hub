package ru.practicum.moviehub.http;

public enum HttpStatus {
    OK(200),
    CREATED(201),
    BAD_REQUEST(400),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    UNSUPPORTED_MEDIA_TYPE(415),
    UNPROCESSABLE_ENTITY(422),
    INTERNAL_SERVER_ERROR(500);

    private final int code;

    HttpStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
