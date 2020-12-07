/*
 * CapturingWriterTest.java
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.commons.io.output.BrokenWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.CapturingWriter.Builder;

@SuppressWarnings("nls")
class CapturingWriterTest extends TestBase {

    @Nested
    class Unlimited {

        @Test
        @DisplayName("write(int)")
        void testWriteChar() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (char c : SOURCE.toCharArray()) {
                    writer.write(c);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(char[])")
        void testWriteCharArray() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    writer.write(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())).toCharArray());
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(char[], int, int)")
        void testWriteCharArrayPortion() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    int len = Math.min(10, SOURCE.length() - i);
                    writer.write(SOURCE.toCharArray(), i, len);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(String)")
        void testWriteString() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    writer.write(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())));
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(String, int, int)")
        void testWriteStringPortion() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    int len = Math.min(10, SOURCE.length() - i);
                    writer.write(SOURCE, i, len);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Nested
        @DisplayName("append(CharSequence)")
        class AppendCharSequence {

            @Test
            @DisplayName("non-null")
            void testAppendNonNullCharSequence() throws IOException {
                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw)) {
                    for (int i = 0; i < SOURCE.length(); i += 10) {
                        writer.append(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())));
                    }
                    writer.flush();
                    assertEquals(SOURCE, sw.toString());
                }
            }

            @Test
            @DisplayName("null")
            void testAppendNullCharSequence() throws IOException {
                Consumer<CapturingWriter> callback = writer -> {
                    assertEquals("nullnullnull", writer.captured());
                    assertEquals(12, writer.totalChars());
                    assertTrue(writer.isClosed());
                };

                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw, callback)) {
                    writer.append(null);
                    writer.append(null);
                    writer.append(null);

                    writer.flush();
                    assertEquals("nullnullnull", sw.toString());
                }
            }
        }

        @Nested
        @DisplayName("append(CharSequence, int, int)")
        class AppendCharSequencePortion {

            @Test
            @DisplayName("non-null")
            void testAppendNonNullCharSequencePortion() throws IOException {
                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw)) {
                    for (int i = 0; i < SOURCE.length(); i += 10) {
                        int len = Math.min(10, SOURCE.length() - i);
                        writer.append(SOURCE, i, i + len);
                    }
                    writer.flush();
                    assertEquals(SOURCE, sw.toString());
                }
            }

            @Test
            @DisplayName("null")
            void testAppendNullCharSequence() throws IOException {
                Consumer<CapturingWriter> callback = writer -> {
                    assertEquals("ululul", writer.captured());
                    assertEquals(6, writer.totalChars());
                    assertTrue(writer.isClosed());
                };

                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw, callback)) {
                    writer.append(null, 1, 3);
                    writer.append(null, 1, 3);
                    writer.append(null, 1, 3);

                    writer.flush();
                    assertEquals("ululul", sw.toString());
                }
            }
        }

        @Test
        @DisplayName("append(char)")
        void testAppendChar() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (char c : SOURCE.toCharArray()) {
                    writer.append(c);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingWriter> callback = writer -> {
                counter.incrementAndGet();
                assertTrue(writer.isClosed());
            };

            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw, callback)) {
                writer.close();
            }
            assertEquals(1, counter.get());
        }

        private Writer createWriter(Writer delegate) {
            return createWriter(delegate, writer -> {
                assertEquals(SOURCE, writer.captured());
                assertEquals(SOURCE.length(), writer.totalChars());
                assertTrue(writer.isClosed());
            });
        }

        private Writer createWriter(Writer delegate, Consumer<CapturingWriter> doneCallback) {
            AtomicInteger doneCount = new AtomicInteger(0);
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            return new CapturingWriter(delegate, CapturingWriter.config()
                    .onDone(writer -> {
                        assertEquals(0, doneCount.getAndIncrement());
                        assertEquals(0, limitReachedCount.get());
                        doneCallback.accept(writer);
                    })
                    .onLimitReached(writer -> {
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
        void testWriteChar() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (char c : SOURCE.toCharArray()) {
                    writer.write(c);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(char[])")
        void testWriteCharArray() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    writer.write(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())).toCharArray());
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(char[], int, int)")
        void testWriteCharArrayPortion() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    int len = Math.min(10, SOURCE.length() - i);
                    writer.write(SOURCE.toCharArray(), i, len);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(String)")
        void testWriteString() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    writer.write(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())));
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("write(String, int, int)")
        void testWriteStringPortion() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (int i = 0; i < SOURCE.length(); i += 10) {
                    int len = Math.min(10, SOURCE.length() - i);
                    writer.write(SOURCE, i, len);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Nested
        @DisplayName("append(CharSequence)")
        class AppendCharSequence {

            @Test
            @DisplayName("non-null")
            void testAppendNonNullCharSequence() throws IOException {
                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw)) {
                    for (int i = 0; i < SOURCE.length(); i += 10) {
                        writer.append(SOURCE.substring(i, Math.min(i + 10, SOURCE.length())));
                    }
                    writer.flush();
                    assertEquals(SOURCE, sw.toString());
                }
            }

            @Test
            @DisplayName("null")
            void testAppendNullCharSequence() throws IOException {
                Consumer<CapturingWriter> callback = writer -> {
                    assertEquals("nulln", writer.captured());
                    assertEquals(12, writer.totalChars());
                    assertTrue(writer.isClosed());
                };

                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw, 5, callback)) {
                    writer.append(null);
                    writer.append(null);
                    writer.append(null);

                    writer.flush();
                    assertEquals("nullnullnull", sw.toString());
                }
            }
        }

        @Nested
        @DisplayName("append(CharSequence, int, int)")
        class AppendCharSequencePortion {

            @Test
            @DisplayName("non-null")
            void testAppendNonNullCharSequencePortion() throws IOException {
                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw)) {
                    for (int i = 0; i < SOURCE.length(); i += 10) {
                        int len = Math.min(10, SOURCE.length() - i);
                        writer.append(SOURCE, i, i + len);
                    }
                    writer.flush();
                    assertEquals(SOURCE, sw.toString());
                }
            }

            @Test
            @DisplayName("null")
            void testAppendNullCharSequence() throws IOException {
                Consumer<CapturingWriter> callback = writer -> {
                    assertEquals("ululu", writer.captured());
                    assertEquals(6, writer.totalChars());
                    assertTrue(writer.isClosed());
                };

                StringWriter sw = new StringWriter(SOURCE.length());
                try (Writer writer = createWriter(sw, 5, callback)) {
                    writer.append(null, 1, 3);
                    writer.append(null, 1, 3);
                    writer.append(null, 1, 3);

                    writer.flush();
                    assertEquals("ululul", sw.toString());
                }
            }
        }

        @Test
        @DisplayName("append(char)")
        void testAppendChar() throws IOException {
            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw)) {
                for (char c : SOURCE.toCharArray()) {
                    writer.append(c);
                }
                writer.flush();
                assertEquals(SOURCE, sw.toString());
            }
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() throws IOException {
            AtomicInteger counter = new AtomicInteger(0);
            Consumer<CapturingWriter> callback = writer -> {
                counter.incrementAndGet();
                assertTrue(writer.isClosed());
            };

            StringWriter sw = new StringWriter(SOURCE.length());
            try (Writer writer = createWriter(sw, 13, callback)) {
                writer.close();
            }
            assertEquals(1, counter.get());
        }

        private Writer createWriter(Writer delegate) {
            int limit = 13;
            AtomicInteger limitReachedCount = new AtomicInteger(0);

            Consumer<CapturingWriter> doneCallback = writer -> {
                assertEquals(1, limitReachedCount.get());
                assertEquals(SOURCE.substring(0, limit), writer.captured());
                assertEquals(SOURCE.length(), writer.totalChars());
            };
            Consumer<CapturingWriter> limitReachedCallback = writer -> {
                assertEquals(0, limitReachedCount.getAndIncrement());
            };
            return createWriter(delegate, limit, doneCallback, limitReachedCallback);
        }

        private Writer createWriter(Writer delegate, int limit, Consumer<CapturingWriter> doneCallback) {
            Consumer<CapturingWriter> limitReachedCallback = writer -> { /* do nothing */ };
            return createWriter(delegate, limit, doneCallback, limitReachedCallback);
        }

        private Writer createWriter(Writer delegate, int limit, Consumer<CapturingWriter> doneCallback,
                Consumer<CapturingWriter> limitReachedCallback) {

            AtomicInteger doneCount = new AtomicInteger(0);

            return new CapturingWriter(delegate, CapturingWriter.config()
                    .withLimit(limit)
                    .withExpectedCount(SOURCE.length())
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
            void testWriteChar() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.write(0);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(char[])")
            void testWriteCharArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.write(new char[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(char[], int, int)")
            void testWriteCharArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.write(new char[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(String)")
            void testWriteString() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.write(SOURCE);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("write(String, int, int)")
            void testWriteStringPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.write(SOURCE, 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: write + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("append(CharSequence)")
            void testAppendCharSequence() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.append(SOURCE);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: append + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("append(CharSequence, int, int)")
            void testAppendCharSequencePortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.append(SOURCE, 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: append + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("append(char)")
            void testAppendChar() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.append('\0');
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: append + close
                assertEquals(2, errorCount.get());
            }

            @Test
            @DisplayName("flush()")
            void testFlush() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount, errorCount)) {
                        writer.flush();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
                // 2: flush + close
                assertEquals(2, errorCount.get());
            }

            @SuppressWarnings("resource")
            private Writer createWriter(AtomicInteger doneCount, AtomicInteger limitReachedCount, AtomicInteger errorCount) {
                return new CapturingWriter(new BrokenWriter(), CapturingWriter.config()
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
            void testWriteChar() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.write(0);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(char[])")
            void testWriteCharArray() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.write(new char[10]);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(char[], int, int)")
            void testWriteCharArrayPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.write(new char[20], 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(String)")
            void testWriteString() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.write(SOURCE);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("write(String, int, int)")
            void testWriteStringPortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.write(SOURCE, 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("append(CharSequence)")
            void testAppendCharSequence() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.append(SOURCE);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("append(CharSequence, int, int)")
            void testAppendCharSequencePortion() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.append(SOURCE, 5, 10);
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("append(char)")
            void testAppendChar() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.append('\0');
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @Test
            @DisplayName("flush()")
            void testFlush() {
                AtomicInteger doneCount = new AtomicInteger(0);
                AtomicInteger limitReachedCount = new AtomicInteger(0);
                assertThrows(IOException.class, () -> {
                    try (Writer writer = createWriter(doneCount, limitReachedCount)) {
                        writer.flush();
                    }
                });
                assertEquals(0, doneCount.get());
                assertEquals(0, limitReachedCount.get());
            }

            @SuppressWarnings("resource")
            private Writer createWriter(AtomicInteger doneCount, AtomicInteger limitReachedCount) {
                return new CapturingWriter(new BrokenWriter(), CapturingWriter.config()
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
            Builder builder = CapturingWriter.config();
            assertThrows(IllegalArgumentException.class, () -> builder.withLimit(-1));
        }
    }
}
