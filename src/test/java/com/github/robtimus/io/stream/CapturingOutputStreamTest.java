/*
 * CapturingOutputStreamTest.java
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import org.apache.commons.io.output.BrokenOutputStream;
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
import com.github.robtimus.io.stream.CapturingOutputStream.Builder;
import com.github.robtimus.io.stream.jaxrs.TestApplication;
import com.github.robtimus.io.stream.web.TestFilter;

@SuppressWarnings("nls")
class CapturingOutputStreamTest extends TestBase {

    static final byte[] INPUT = SOURCE.getBytes();

    @Nested
    class Unlimited {

        @Test
        @DisplayName("write(int)")
        void testWriteByte() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (byte b : INPUT) {
                    output.write(b);
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("write(byte[])")
        void testWriteByteArray() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (int i = 0; i < INPUT.length; i += 10) {
                    output.write(Arrays.copyOfRange(INPUT, i, Math.min(i + 10, INPUT.length)));
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        void testWriteByteArrayPortion() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (int i = 0; i < INPUT.length; i += 10) {
                    int len = Math.min(10, INPUT.length - i);
                    output.write(INPUT, i, len);
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingOutputStream> callback = output -> {
                counter.incrementAndGet();
                assertTrue(output.isClosed());
            };

            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos, callback)) {
                output.close();
            }
            assertEquals(1, counter.get());
        }

        private OutputStream createOutputStream(OutputStream delegate) {
            return createOutputStream(delegate, output -> {
                assertArrayEquals(INPUT, output.captured());
                assertEquals(SOURCE, output.captured(StandardCharsets.UTF_8));
                assertEquals(INPUT.length, output.totalBytes());
                assertTrue(output.isClosed());
            });
        }

        private OutputStream createOutputStream(OutputStream delegate, Consumer<CapturingOutputStream> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingOutputStream(delegate, CapturingOutputStream.config()
                    .onDone(output -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(output);
                    })
                    .onLimitReached(output -> {
                        assertEquals(0, doneCount.get());
                        assertEquals(0, limitReachedCount.getAndIncrement());
                    })
                    .build());
        }
    }

    @Nested
    class Limited {

        @Test
        @DisplayName("write(int)")
        void testWriteByte() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (byte b : INPUT) {
                    output.write(b);
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("write(byte[])")
        void testWriteByteArray() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (int i = 0; i < INPUT.length; i += 10) {
                    output.write(Arrays.copyOfRange(INPUT, i, Math.min(i + 10, INPUT.length)));
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        void testWriteByteArrayPortion() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos)) {
                for (int i = 0; i < INPUT.length; i += 10) {
                    int len = Math.min(10, INPUT.length - i);
                    output.write(INPUT, i, len);
                }
                output.flush();
                assertArrayEquals(INPUT, baos.toByteArray());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingOutputStream> callback = output -> {
                counter.incrementAndGet();
                assertTrue(output.isClosed());
            };

            ByteArrayOutputStream baos = new ByteArrayOutputStream(INPUT.length);
            try (OutputStream output = createOutputStream(baos, 13, callback)) {
                output.close();
            }
            assertEquals(1, counter.get());
        }

        private OutputStream createOutputStream(OutputStream delegate) {
            int limit = 13;
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            Consumer<CapturingOutputStream> doneCallback = output -> {
                assertEquals(1, limitReachedCount.get());
                assertArrayEquals(Arrays.copyOfRange(INPUT, 0, limit), output.captured());
                assertEquals(SOURCE.substring(0, limit), output.captured(StandardCharsets.UTF_8));
                assertEquals(INPUT.length, output.totalBytes());
            };
            Consumer<CapturingOutputStream> limitReachedCallback = output -> {
                assertEquals(0, limitReachedCount.getAndIncrement());
            };
            return createOutputStream(delegate, limit, doneCallback, limitReachedCallback);
        }

        private OutputStream createOutputStream(OutputStream delegate, int limit, Consumer<CapturingOutputStream> doneCallback) {
            Consumer<CapturingOutputStream> limitReachedCallback = output -> { /* do nothing */ };
            return createOutputStream(delegate, limit, doneCallback, limitReachedCallback);
        }

        private OutputStream createOutputStream(OutputStream delegate, int limit, Consumer<CapturingOutputStream> doneCallback,
                Consumer<CapturingOutputStream> limitReachedCallback) {

            AtomicInteger doneCount = new AtomicInteger(0);

            return new CapturingOutputStream(delegate, CapturingOutputStream.config()
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
    class WithErrors {

        @Nested
        class WithErrorHandler {

            @Test
            @DisplayName("write(int)")
            void testWriteByte() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount, errorCount)) {
                        output.write(0);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(byte[])")
            void testWriteByteArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount, errorCount)) {
                        output.write(new byte[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(byte[], int, int)")
            void testWriteByteArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount, errorCount)) {
                        output.write(new byte[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("flush")
            void testFlush() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount, errorCount)) {
                        output.flush();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: flush + close
                assertEquals(2, errorCount.get());
            }

            @SuppressWarnings("resource")
            private OutputStream createOutputStream(AtomicInteger doneCount, AtomicInteger limitReachedCount, AtomicInteger errorCount) {
                return new CapturingOutputStream(new BrokenOutputStream(), CapturingOutputStream.config()
                        .onDone(input -> doneCount.getAndIncrement())
                        .onLimitReached(input -> limitReachedCount.getAndIncrement())
                        .onError((input, error) -> errorCount.getAndIncrement())
                        .build());
            }
        }

        @Nested
        class WithoutErrorHandler {

            @Test
            @DisplayName("write(int)")
            void testWriteByte() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount)) {
                        output.write(0);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(byte[])")
            void testWriteByteArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount)) {
                        output.write(new byte[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(byte[], int, int)")
            void testWriteByteArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount)) {
                        output.write(new byte[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("flush")
            void testFlush() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (OutputStream output = createOutputStream(doneCount, limitReachedCount)) {
                        output.flush();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @SuppressWarnings("resource")
            private OutputStream createOutputStream(AtomicInteger doneCount, AtomicInteger limitReachedCount) {
                return new CapturingOutputStream(new BrokenOutputStream(), CapturingOutputStream.config()
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
            Builder builder = CapturingOutputStream.config();
            assertThrows(IllegalArgumentException.class, () -> builder.withLimit(-1));
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

            assertEquals("{\"val", testFilter.capturedFromResponse());
            assertEquals(12, testFilter.totalResponseBytes());
        }
    }
}
