/*
 * CapturingReaderTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.CapturingReader.Builder;

class CapturingReaderTest extends TestBase {

    @Nested
    class Unlimited {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[])")
        void testReadIntoCharArray() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[10];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadIntoCharArrayPortion() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[20];
                int len;
                while ((len = reader.read(buffer, 5, 10)) != -1) {
                    sb.append(buffer, 5, len);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (Reader reader = createReader()) {
                assertTrue(reader.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                StringBuilder expectedContent = new StringBuilder(SOURCE.length() * 3 / 2);
                for (int i = 0; i < SOURCE.length(); i += readSize * 2) {
                    expectedContent.append(SOURCE, i, Math.min(i + readSize, SOURCE.length()));
                    expectedContent.append(SOURCE, i, Math.min(i + readSize * 2, SOURCE.length()));
                }

                StringBuilder sb = new StringBuilder(expectedContent.length());

                char[] markedBuffer = new char[readSize];
                char[] buffer = new char[readSize * 2];
                int len;
                reader.mark(readSize);
                while ((len = reader.read(markedBuffer)) != -1) {
                    sb.append(markedBuffer, 0, len);
                    reader.reset();

                    len = reader.read(buffer);
                    if (len != -1) {
                        sb.append(buffer, 0, len);
                        reader.mark(readSize);
                    }
                }
                assertEquals(expectedContent.toString(), sb.toString());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingReader> callback = reader -> {
                counter.incrementAndGet();
                assertFalse(reader.isConsumed());
                assertTrue(reader.isClosed());
            };

            try (Reader reader = createReader(callback)) {
                reader.close();
            }
            assertEquals(1, counter.get());
        }

        private Reader createReader() {
            return createReader(reader -> {
                assertEquals(SOURCE, reader.captured());
                assertEquals(SOURCE.length(), reader.totalChars());
                assertTrue(reader.isConsumed());
                assertFalse(reader.isClosed());
            });
        }

        private Reader createReader(Consumer<CapturingReader> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingReader(new StringReader(SOURCE), CapturingReader.config()
                    .onDone(reader -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(reader);
                    })
                    .onLimitReached(reader -> {
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
        void testReadChar() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
                assertEquals(-1, reader.read());
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[])")
        void testReadIntoCharArray() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[10];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
                assertEquals(-1, reader.read(buffer));
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadIntoCharArrayPortion() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[20];
                int len;
                while ((len = reader.read(buffer, 5, 10)) != -1) {
                    sb.append(buffer, 5, len);
                }
                assertEquals(-1, reader.read(buffer, 5, 10));
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (Reader reader = createReader()) {
                assertTrue(reader.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                StringBuilder expectedContent = new StringBuilder(SOURCE.length() * 3 / 2);
                for (int i = 0; i < SOURCE.length(); i += readSize * 2) {
                    expectedContent.append(SOURCE, i, Math.min(i + readSize, SOURCE.length()));
                    expectedContent.append(SOURCE, i, Math.min(i + readSize * 2, SOURCE.length()));
                }

                StringBuilder sb = new StringBuilder(expectedContent.length());

                char[] markedBuffer = new char[readSize];
                char[] buffer = new char[readSize * 2];
                int len;
                reader.mark(readSize);
                while ((len = reader.read(markedBuffer)) != -1) {
                    sb.append(markedBuffer, 0, len);
                    reader.reset();

                    len = reader.read(buffer);
                    if (len != -1) {
                        sb.append(buffer, 0, len);
                        reader.mark(readSize);
                    }
                }
                assertEquals(expectedContent.toString(), sb.toString());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingReader> callback = reader -> {
                counter.incrementAndGet();
                assertFalse(reader.isConsumed());
                assertTrue(reader.isClosed());
            };

            try (Reader reader = createReader(13, callback)) {
                reader.close();
            }
            assertEquals(1, counter.get());
        }

        private Reader createReader() {
            int limit = 13;
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            Consumer<CapturingReader> doneCallback = reader -> {
                assertEquals(1, limitReachedCount.get());
                assertEquals(SOURCE.substring(0, limit), reader.captured());
                assertEquals(SOURCE.length(), reader.totalChars());
            };
            Consumer<CapturingReader> limitReachedCallback = reader -> {
                assertEquals(0, limitReachedCount.getAndIncrement());
            };
            return createReader(limit, doneCallback, limitReachedCallback);
        }

        private Reader createReader(int limit, Consumer<CapturingReader> doneCallback) {
            Consumer<CapturingReader> limitReachedCallback = reader -> { /* do nothing */ };
            return createReader(limit, doneCallback, limitReachedCallback);
        }

        private Reader createReader(int limit, Consumer<CapturingReader> doneCallback,
                Consumer<CapturingReader> limitReachedCallback) {

            AtomicInteger doneCount = new AtomicInteger(0);

            return new CapturingReader(new StringReader(SOURCE), CapturingReader.config()
                    .withLimit(limit)
                    .withExpectedCount(SOURCE.length())
                    .onDone(reader -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        doneCallback.accept(reader);
                    })
                    .onLimitReached(reader -> {
                        assertEquals(0, doneCount.get());
                        limitReachedCallback.accept(reader);
                    })
                    .build());
        }
    }

    @Nested
    class DoneAfter {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[])")
        void testReadIntoCharArray() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[10];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadIntoCharArrayPortion() throws IOException {
            try (Reader reader = createReader()) {
                StringBuilder sb = new StringBuilder(SOURCE.length());

                char[] buffer = new char[20];
                int len;
                while ((len = reader.read(buffer, 5, 10)) != -1) {
                    sb.append(buffer, 5, len);
                }
                assertEquals(SOURCE, sb.toString());
            }
        }

        @Test
        @DisplayName("mark and reset")
        void testMarkAndReset() throws IOException {
            try (Reader reader = createReader()) {
                assertTrue(reader.markSupported());

                // mark, read 5, reset, read 10, repeat
                final int readSize = 5;

                StringBuilder expectedContent = new StringBuilder(SOURCE.length() * 3 / 2);
                for (int i = 0; i < SOURCE.length(); i += readSize * 2) {
                    expectedContent.append(SOURCE, i, Math.min(i + readSize, SOURCE.length()));
                    expectedContent.append(SOURCE, i, Math.min(i + readSize * 2, SOURCE.length()));
                }

                StringBuilder sb = new StringBuilder(expectedContent.length());

                char[] markedBuffer = new char[readSize];
                char[] buffer = new char[readSize * 2];
                int len;
                reader.mark(readSize);
                while ((len = reader.read(markedBuffer)) != -1) {
                    sb.append(markedBuffer, 0, len);
                    reader.reset();

                    len = reader.read(buffer);
                    if (len != -1) {
                        sb.append(buffer, 0, len);
                        reader.mark(readSize);
                    }
                }
                assertEquals(expectedContent.toString(), sb.toString());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingReader> callback = reader -> {
                counter.incrementAndGet();
                assertFalse(reader.isConsumed());
                assertTrue(reader.isClosed());
            };

            try (Reader reader = createReader(callback)) {
                reader.close();
            }
            assertEquals(1, counter.get());
        }

        private Reader createReader() {
            return createReader(reader -> {
                assertEquals(SOURCE.substring(0, SOURCE.length() - 5), reader.captured());
                assertEquals(SOURCE.length() - 5, reader.totalChars());
                assertFalse(reader.isConsumed());
                assertFalse(reader.isClosed());
            });
        }

        private Reader createReader(Consumer<CapturingReader> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingReader(new StringReader(SOURCE), CapturingReader.config()
                    .doneAfter(SOURCE.length() - 5)
                    .onDone(reader -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(reader);
                    })
                    .onLimitReached(reader -> {
                        assertEquals(0, doneCount.get());
                        assertEquals(0, limitReachedCount.getAndIncrement());
                    })
                    .build());
        }
    }

    @Nested
    class BuilderTest {

        @Test
        @DisplayName("negative limit")
        void testNegativeLimit() {
            Builder builder = CapturingReader.config();
            assertThrows(IllegalArgumentException.class, () -> builder.withLimit(-1));
        }

        @Test
        @DisplayName("doneAfter with negative doneAfter")
        void testDoneWithNegativeDoneAfter() {
            Builder builder = CapturingReader.config();
            assertThrows(IllegalArgumentException.class, () -> builder.doneAfter(-1));
        }
    }
}
