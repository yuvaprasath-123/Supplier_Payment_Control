package com.ofss.internal;

import jakarta.servlet.http.*;
import java.io.IOException;

/** Implemented by each host's existing sanitized JSON error writer. */
public interface InternalErrorWriter {
    void unauthorized(HttpServletRequest request, HttpServletResponse response, boolean invalidToken) throws IOException;
    void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException;
}
