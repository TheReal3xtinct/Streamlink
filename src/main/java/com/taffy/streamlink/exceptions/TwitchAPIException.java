package com.taffy.streamlink.exceptions;

public class TwitchAPIException extends Exception {
    public TwitchAPIException(String message) {
        super(message);
    }

    public TwitchAPIException(String message, Throwable cause) {
        super(message, cause);
    }

    public TwitchAPIException(int statusCode, String message) {
        super("HTTP " + statusCode + ": " + message);
    }
}
