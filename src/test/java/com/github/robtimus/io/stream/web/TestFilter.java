/*
 * TestFilter.java
 * Copyright 2020 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.io.stream.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import com.github.robtimus.io.stream.CapturingInputStream;
import com.github.robtimus.io.stream.CapturingOutputStream;

@SuppressWarnings("javadoc")
public class TestFilter implements Filter {

    private String capturedFromRequest;
    private long totalRequestBytes;

    private String capturedFromResponse;
    private long totalResponseBytes;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // does nothing
    }

    @Override
    public void destroy() {
        // does nothing
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        CapturingHttpServletRequest capturingRequest = new CapturingHttpServletRequest(httpRequest);
        CapturingHttpServletResponse capturingResponse = new CapturingHttpServletResponse(httpResponse);

        try {
            chain.doFilter(capturingRequest, capturingResponse);
        } finally {
            System.err.printf("Done in filter%n");
            capturingResponse.done();
        }
    }

    private final class CapturingHttpServletRequest extends HttpServletRequestWrapper {

        private ServletInputStream inputStream = null;

        private CapturingHttpServletRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                CapturingInputStream capturing = new CapturingInputStream(super.getInputStream(), CapturingInputStream.config()
                        .withLimit(5)
                        .withExpectedCount(getContentLength())
                        .doneAfter(getContentLengthLong())
                        .onDone(input -> {
                            capturedFromRequest = input.captured(StandardCharsets.UTF_8);
                            totalRequestBytes = input.totalBytes();
                        })
                        .build());
                return new ServletInputStream() {

                    private boolean finished = false;

                    @Override
                    public int read() throws IOException {
                        int b = capturing.read();
                        finished = finished || b == -1;
                        return b;
                    }

                    @Override
                    public int read(byte[] b) throws IOException {
                        int n = capturing.read(b);
                        finished = finished || n == -1;
                        return n;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        int n = capturing.read(b, off, len);
                        finished = finished || n == -1;
                        return n;
                    }

                    @Override
                    public void close() throws IOException {
                        capturing.close();
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public boolean isFinished() {
                        return finished;
                    }
                };
            }
            return inputStream;
        }
    }

    private final class CapturingHttpServletResponse extends HttpServletResponseWrapper {

        private CapturingOutputStream capturingOutputStream;
        private ServletOutputStream outputStream;

        private CapturingHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        @SuppressWarnings("resource")
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                capturingOutputStream = new CapturingOutputStream(super.getOutputStream(), CapturingOutputStream.config()
                        .withLimit(5)
                        .onDone(input -> {
                            capturedFromResponse = input.captured(StandardCharsets.UTF_8);
                            totalResponseBytes = input.totalBytes();
                            System.err.printf("onDone; captured = %s (%d)%n", capturedFromResponse, totalResponseBytes);
                        })
                        .build());
                outputStream = new ServletOutputStream() {

                    @Override
                    public void write(int b) throws IOException {
                        capturingOutputStream.write(b);
                    }

                    @Override
                    public void write(byte[] b) throws IOException {
                        capturingOutputStream.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        capturingOutputStream.write(b, off, len);
                    }

                    @Override
                    public void flush() throws IOException {
                        capturingOutputStream.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        capturingOutputStream.close();
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }
                };
            }
            return outputStream;
        }

        private void done() {
            if (capturingOutputStream != null) {
                capturingOutputStream.done();
            }
        }
    }

    public String capturedFromRequest() {
        return capturedFromRequest;
    }

    public long totalRequestBytes() {
        return totalRequestBytes;
    }

    public String capturedFromResponse() {
        return capturedFromResponse;
    }

    public long totalResponseBytes() {
        return totalResponseBytes;
    }
}
