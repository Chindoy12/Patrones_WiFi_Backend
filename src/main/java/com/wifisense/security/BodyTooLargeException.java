package com.wifisense.security;

import java.io.IOException;

/** A request body grew past its limit while being read (bodies sent without Content-Length, in chunks). */
public class BodyTooLargeException extends IOException {

    public BodyTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
    }
}
