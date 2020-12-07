/*
 * CapturingInputStreamTest.java
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

package com.github.robtimus.io.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import org.apache.commons.io.input.BrokenInputStream;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.servlet.ServletContainer;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.CapturingInputStream.Builder;
import com.github.robtimus.io.stream.jaxrs.TestApplication;
import com.github.robtimus.io.stream.web.TestFilter;

@SuppressWarnings("nls")
class CapturingInputStreamTest extends TestBase {

    static final byte[] INPUT = SOURCE.getBytes();

    @Nested
    class Unlimited {

        @Test
        @DisplayName("read()")
        void testReadByte() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                int b;
                while ((b = input.read()) != -1) {
                    baos.write(b);
                }
                assertEquals(-1, input.read());
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[])")
        void testReadIntoByteArray() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[10];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                assertEquals(-1, input.read(buffer));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadIntoByteArrayPortion() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[20];
                int len;
                while ((len = input.read(buffer, 5, 10)) != -1) {
                    baos.write(buffer, 5, len);
                }
                assertEquals(-1, input.read(buffer, 5, 10));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (InputStream input = createInputStream()) {
                assertTrue(input.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                ByteArrayOutputStream expectedContent = new ByteArrayOutputStream(INPUT.length * 3 / 2);
                for (int i = 0; i < INPUT.length; i += readSize * 2) {
                    expectedContent.write(INPUT, i, Math.min(readSize, INPUT.length - i));
                    expectedContent.write(INPUT, i, Math.min(readSize * 2, INPUT.length - i));
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedContent.size());

                byte[] markedBuffer = new byte[readSize];
                byte[] buffer = new byte[readSize * 2];
                int len;
                input.mark(readSize);
                while ((len = input.read(markedBuffer)) != -1) {
                    baos.write(markedBuffer, 0, len);
                    input.reset();

                    len = input.read(buffer);
                    if (len != -1) {
                        baos.write(buffer, 0, len);
                        input.mark(readSize);
                    }
                }
                assertArrayEquals(expectedContent.toByteArray(), baos.toByteArray());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingInputStream> callback = input -> {
                counter.incrementAndGet();
                assertFalse(input.isConsumed());
                assertTrue(input.isClosed());
            };

            try (InputStream input = createInputStream(callback)) {
                input.close();
            }
            assertEquals(1, counter.get());
        }

        private InputStream createInputStream() {
            return createInputStream(input -> {
                assertArrayEquals(INPUT, input.captured());
                assertEquals(SOURCE, input.captured(StandardCharsets.UTF_8));
                assertEquals(INPUT.length, input.totalBytes());
                assertTrue(input.isConsumed());
                assertFalse(input.isClosed());
            });
        }

        private InputStream createInputStream(Consumer<CapturingInputStream> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingInputStream(new ByteArrayInputStream(INPUT), CapturingInputStream.config()
                    .onDone(input -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(input);
                    })
                    .onLimitReached(input -> {
                        assertEquals(0, doneCount.get());
                        assertEquals(0, limitReachedCount.getAndIncrement());
                    })
                    .build());
        }
    }

    @Nested
    class Limited {

        @Test
        @DisplayName("read()")
        void testReadByte() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                int b;
                while ((b = input.read()) != -1) {
                    baos.write(b);
                }
                assertEquals(-1, input.read());
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[])")
        void testReadIntoByteArray() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[10];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                assertEquals(-1, input.read(buffer));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadIntoByteArrayPortion() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[20];
                int len;
                while ((len = input.read(buffer, 5, 10)) != -1) {
                    baos.write(buffer, 5, len);
                }
                assertEquals(-1, input.read(buffer, 5, 10));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (InputStream input = createInputStream()) {
                assertTrue(input.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                ByteArrayOutputStream expectedContent = new ByteArrayOutputStream(INPUT.length * 3 / 2);
                for (int i = 0; i < INPUT.length; i += readSize * 2) {
                    expectedContent.write(INPUT, i, Math.min(readSize, INPUT.length - i));
                    expectedContent.write(INPUT, i, Math.min(readSize * 2, INPUT.length - i));
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedContent.size());

                byte[] markedBuffer = new byte[readSize];
                byte[] buffer = new byte[readSize * 2];
                int len;
                input.mark(readSize);
                while ((len = input.read(markedBuffer)) != -1) {
                    baos.write(markedBuffer, 0, len);
                    input.reset();

                    len = input.read(buffer);
                    if (len != -1) {
                        baos.write(buffer, 0, len);
                        input.mark(readSize);
                    }
                }
                assertArrayEquals(expectedContent.toByteArray(), baos.toByteArray());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingInputStream> callback = input -> {
                counter.incrementAndGet();
                assertFalse(input.isConsumed());
                assertTrue(input.isClosed());
            };

            try (InputStream input = createInputStream(13, callback)) {
                input.close();
            }
            assertEquals(1, counter.get());
        }

        private InputStream createInputStream() {
            int limit = 13;
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            Consumer<CapturingInputStream> doneCallback = input -> {
                assertEquals(1, limitReachedCount.get());
                assertArrayEquals(Arrays.copyOfRange(INPUT, 0, limit), input.captured());
                assertEquals(SOURCE.substring(0, limit), input.captured(StandardCharsets.UTF_8));
                assertEquals(INPUT.length, input.totalBytes());
            };
            Consumer<CapturingInputStream> limitReachedCallback = input -> {
                assertEquals(0, limitReachedCount.getAndIncrement());
            };
            return createInputStream(limit, doneCallback, limitReachedCallback);
        }

        private InputStream createInputStream(int limit, Consumer<CapturingInputStream> doneCallback) {
            Consumer<CapturingInputStream> limitReachedCallback = input -> { /* do nothing */ };
            return createInputStream(limit, doneCallback, limitReachedCallback);
        }

        private InputStream createInputStream(int limit, Consumer<CapturingInputStream> doneCallback,
                Consumer<CapturingInputStream> limitReachedCallback) {

            AtomicInteger doneCount = new AtomicInteger(0);

            return new CapturingInputStream(new ByteArrayInputStream(INPUT), CapturingInputStream.config()
                    .withLimit(limit)
                    .withExpectedCount(INPUT.length)
                    .onDone(input -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        doneCallback.accept(input);
                    })
                    .onLimitReached(input -> {
                        assertEquals(0, doneCount.get());
                        limitReachedCallback.accept(input);
                    })
                    .build());
        }
    }

    @Nested
    class DoneAfter {

        @Test
        @DisplayName("read()")
        void testReadByte() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                int b;
                while ((b = input.read()) != -1) {
                    baos.write(b);
                }
                assertEquals(-1, input.read());
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[])")
        void testReadIntoByteArray() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[10];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                assertEquals(-1, input.read(buffer));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadIntoByteArrayPortion() throws IOException {
            try (InputStream input = createInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);

                byte[] buffer = new byte[20];
                int len;
                while ((len = input.read(buffer, 5, 10)) != -1) {
                    baos.write(buffer, 5, len);
                }
                assertEquals(-1, input.read(buffer, 5, 10));
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (InputStream input = createInputStream()) {
                assertTrue(input.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                ByteArrayOutputStream expectedContent = new ByteArrayOutputStream(INPUT.length * 3 / 2);
                for (int i = 0; i < INPUT.length; i += readSize * 2) {
                    expectedContent.write(INPUT, i, Math.min(readSize, INPUT.length - i));
                    expectedContent.write(INPUT, i, Math.min(readSize * 2, INPUT.length - i));
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedContent.size());

                byte[] markedBuffer = new byte[readSize];
                byte[] buffer = new byte[readSize * 2];
                int len;
                input.mark(readSize);
                while ((len = input.read(markedBuffer)) != -1) {
                    baos.write(markedBuffer, 0, len);
                    input.reset();

                    len = input.read(buffer);
                    if (len != -1) {
                        baos.write(buffer, 0, len);
                        input.mark(readSize);
                    }
                }
                assertArrayEquals(expectedContent.toByteArray(), baos.toByteArray());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingInputStream> callback = input -> {
                counter.incrementAndGet();
                assertFalse(input.isConsumed());
                assertTrue(input.isClosed());
            };

            try (InputStream input = createInputStream(callback)) {
                input.close();
            }
            assertEquals(1, counter.get());
        }

        private InputStream createInputStream() {
            return createInputStream(input -> {
                assertArrayEquals(Arrays.copyOfRange(INPUT, 0, INPUT.length - 5), input.captured());
                assertEquals(SOURCE.substring(0, INPUT.length - 5), input.captured(StandardCharsets.UTF_8));
                assertEquals(INPUT.length - 5, input.totalBytes());
                assertFalse(input.isConsumed());
                assertFalse(input.isClosed());
            });
        }

        private InputStream createInputStream(Consumer<CapturingInputStream> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingInputStream(new ByteArrayInputStream(INPUT), CapturingInputStream.config()
                    .doneAfter(INPUT.length - 5)
                    .onDone(input -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(input);
                    })
                    .onLimitReached(input -> {
                        assertEquals(0, doneCount.get());
                        assertEquals(0, limitReachedCount.getAndIncrement());
                    })
                    .build());
        }
    }

    @Nested
    class WithErrors {

        @Nested
        class WithErrorHandler {

            @Test
            @DisplayName("read()")
            void testReadByte() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount, errorCount)) {
                        input.read();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: read + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("read(byte[])")
            void testReadIntoByteArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount, errorCount)) {
                        input.read(new byte[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: read + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("read(byte[], int, int)")
            void testReadIntoByteArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount, errorCount)) {
                        input.read(new byte[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: read + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("mark and reset")
            void testMarkAndReset() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount, errorCount)) {
                        assertDoesNotThrow(() -> input.mark(5));
                        input.reset();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: reset + close
                assertEquals(2, errorCount.get());
            }

            @SuppressWarnings("resource")
            private InputStream createInputStream(AtomicInteger doneCount, AtomicInteger limitReachedCount, AtomicInteger errorCount) {
                return new CapturingInputStream(new BrokenInputStream(), CapturingInputStream.config()
                        .onDone(input -> doneCount.getAndIncrement())
                        .onLimitReached(input -> limitReachedCount.getAndIncrement())
                        .onError((input, error) -> errorCount.getAndIncrement())
                        .build());
            }
        }

        @Nested
        class WithoutErrorHandler {

            @Test
            @DisplayName("read()")
            void testReadByte() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount)) {
                        input.read();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("read(byte[])")
            void testReadIntoByteArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount)) {
                        input.read(new byte[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("read(byte[], int, int)")
            void testReadIntoByteArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount)) {
                        input.read(new byte[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("mark and reset")
            void testMarkAndReset() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (InputStream input = createInputStream(doneCount, limitReachedCount)) {
                        assertDoesNotThrow(() -> input.mark(5));
                        input.reset();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @SuppressWarnings("resource")
            private InputStream createInputStream(AtomicInteger doneCount, AtomicInteger limitReachedCount) {
                return new CapturingInputStream(new BrokenInputStream(), CapturingInputStream.config()
                        .onDone(input -> doneCount.getAndIncrement())
                        .onLimitReached(input -> limitReachedCount.getAndIncrement())
                        .build());
            }
        }
    }

    @Nested
    class BuilderTest {

        @Test
        @DisplayName("negative limit")
        void testNegativeLimit() {
            Builder builder = CapturingInputStream.config();
            assertThrows(IllegalArgumentException.class, () -> builder.withLimit(-1));
        }

        @Test
        @DisplayName("doneAfter with negative doneAfter")
        void testDoneWithNegativeDoneAfter() {
            Builder builder = CapturingInputStream.config();
            assertThrows(IllegalArgumentException.class, () -> builder.doneAfter(-1));
        }
    }

    @Nested
    class FrameworkTests {

        @Nested
        class Jersey extends JAXRSTest {

            Jersey() {
                super(ServletContainer::new, Collections.singletonMap("javax.ws.rs.Application", TestApplication.class.getName()));
            }
        }

        @Nested
        class RESTEasy extends JAXRSTest {

            RESTEasy() {
                super(HttpServletDispatcher::new, Collections.singletonMap("javax.ws.rs.Application", TestApplication.class.getName()));
            }
        }
    }

    abstract static class JAXRSTest {

        private final Supplier<Servlet> servletFactory;
        private final Map<String, String> initParameters;

        private HttpClient client;
        private Server server;
        private ServerConnector serverConnector;
        private TestFilter testFilter;

        JAXRSTest(Supplier<Servlet> servletFactory) {
            this(servletFactory, Collections.emptyMap());
        }

        JAXRSTest(Supplier<Servlet> servletFactory, Map<String, String> initParameters) {
            this.servletFactory = servletFactory;
            this.initParameters = initParameters;
        }

        @BeforeEach
        void startServer() {
            QueuedThreadPool serverPool = new QueuedThreadPool();
            serverPool.setName("server");
            server = new Server(serverPool);

            HttpConfiguration configuration = new HttpConfiguration();
            configuration.setSendDateHeader(false);
            configuration.setSendServerVersion(false);
            serverConnector = new ServerConnector(server, new HttpConnectionFactory(configuration));
            server.addConnector(serverConnector);

            ServletContextHandler appContext = new ServletContextHandler(server, "/", true, false);
            ServletHolder servletHolder = new ServletHolder(servletFactory.get());
            servletHolder.setInitParameters(initParameters);
            appContext.addServlet(servletHolder, "/*");

            testFilter = new TestFilter();
            FilterHolder filterHolder = new FilterHolder(testFilter);
            appContext.addFilter(filterHolder, "/*", EnumSet.allOf(DispatcherType.class));

            assertDoesNotThrow(server::start);
        }

        @AfterEach
        void stopServer() {
            assertDoesNotThrow(server::stop);
        }

        @BeforeEach
        void startClient() {
            QueuedThreadPool clientPool = new QueuedThreadPool();
            clientPool.setName("client");
            client = new HttpClient();
            client.setExecutor(clientPool);
            assertDoesNotThrow(client::start);
        }

        @AfterEach
        void stopClient() {
            assertDoesNotThrow(client::stop);
        }

        @Test
        @DisplayName("capture")
        void testCapture() {
            ContentResponse response = assertDoesNotThrow(() -> client.newRequest("localhost", serverConnector.getLocalPort())
                    .path("/echo")
                    .method(HttpMethod.POST)
                    .content(new StringContentProvider("application/json", "{\"value\":13}", StandardCharsets.UTF_8))
                    //.timeout(5, TimeUnit.SECONDS)
                    .send());
            assertEquals(200, response.getStatus());

            assertEquals("{\"val", testFilter.capturedFromRequest());
            assertEquals(12, testFilter.totalRequestBytes());
        }
    }
}
