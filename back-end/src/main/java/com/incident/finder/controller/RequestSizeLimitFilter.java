package com.incident.finder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.model.ApiErrorResponse;
import com.incident.finder.service.IncidentSearchProperties;
import com.incident.finder.service.SearchRequestMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final IncidentSearchProperties searchProperties;
    private final SearchRequestMetrics requestMetrics;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            IncidentSearchProperties searchProperties,
            SearchRequestMetrics requestMetrics,
            ObjectMapper objectMapper
    ) {
        this.searchProperties = searchProperties;
        this.requestMetrics = requestMetrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int maximumBytes = searchProperties.getMaximumRequestSizeBytes();
        if (request.getContentLengthLong() > maximumBytes) {
            writePayloadTooLarge(response);
            return;
        }

        filterChain.doFilter(new LimitedRequestWrapper(request, maximumBytes), response);
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        requestMetrics.recordInvalidRequest();
        LOGGER.warn("Rejected incident search request because the request body exceeded the size limit");
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse("Request body exceeds the maximum permitted size.")
        );
    }

    private static class LimitedRequestWrapper extends HttpServletRequestWrapper {

        private final int maximumBytes;
        private ServletInputStream limitedInputStream;
        private BufferedReader reader;

        private LimitedRequestWrapper(HttpServletRequest request, int maximumBytes) {
            super(request);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedInputStream == null) {
                limitedInputStream = new LimitedServletInputStream(
                        super.getInputStream(), maximumBytes
                );
            }
            return limitedInputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(
                        getInputStream(),
                        StandardCharsets.UTF_8
                ));
            }
            return reader;
        }
    }

    private static class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final int maximumBytes;
        private int bytesRead;
        private boolean finished;

        private LimitedServletInputStream(ServletInputStream delegate, int maximumBytes) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maximumBytes) {
                int extraByte = delegate.read();
                if (extraByte == -1) {
                    finished = true;
                    return -1;
                }
                throw new RequestBodyTooLargeException();
            }

            int value = delegate.read();
            if (value == -1) {
                finished = true;
            } else {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (bytesRead >= maximumBytes) {
                read();
                return -1;
            }

            int allowedLength = Math.min(length, maximumBytes - bytesRead);
            int count = delegate.read(buffer, offset, allowedLength);
            if (count == -1) {
                finished = true;
            } else {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return finished || delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new IllegalStateException("Non-blocking request reads are not supported");
        }

        @Override
        public int available() throws IOException {
            return Math.min(
                    delegate.available(),
                    Math.max(0, maximumBytes - bytesRead + 1)
            );
        }
    }

    public static final class RequestBodyTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        private RequestBodyTooLargeException() {
            super("Request body exceeds the maximum permitted size");
        }
    }
}
