/*
 * LimitWriterTest.java
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

import static com.github.robtimus.io.stream.LimitExceededStrategy.THROW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.apache.commons.io.output.NullWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class LimitWriterTest extends TestBase {

    @Test
    @DisplayName("negative limit")
    void testNegativeLimit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new LimitWriter(new NullWriter(), -1));
        assertEquals("-1 < 0", exception.getMessage());
    }

    @Nested
    @DisplayName("Exceed limit with DISCARD strategy")
    class Discard {

        @Test
        @DisplayName("write(int)")
        void testWriteInt() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    writer.write(SOURCE.charAt(i));
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("write(char[], int, int)")
        void testWriteCharArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                char[] chars = SOURCE.toCharArray();
                int index = 0;
                while (index < chars.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    writer.write(chars, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("write(String, int, int)")
        void testWriteStringRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    writer.write(SOURCE, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(CharSequence)")
        void testAppendCharSequence() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    writer.append(SOURCE.substring(index, to));
                    index = to;
                }
            }
            assertEquals(expected, output.toString());

            // append null
            limit = 2;
            expected = "null".substring(0, limit);

            output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                writer.append(null);
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        void testAppendCharSequenceRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    writer.append(SOURCE, index, to);
                    index = to;
                }
            }
            assertEquals(expected, output.toString());

            // append null
            limit = 2;
            expected = "null".substring(0, limit);

            output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                writer.append(null, 0, 3);
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(char)")
        void testAppendChar() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    writer.append(SOURCE.charAt(i));
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("flush()")
        void testFlush() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer writer = new LimitWriter(output, 1)) {
                writer.flush();
            }
            verify(output).flush();
            verify(output).close();
            verifyNoMoreInteractions(output);
        }
    }

    @Nested
    @DisplayName("Exceed limit with THROW strategy")
    class Throw {

        @Test
        @DisplayName("write(int)")
        void testWriteInt() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                for (int i = 0; i < limit; i++) {
                    writer.write(SOURCE.charAt(i));
                }
                for (int i = limit; i < SOURCE.length(); i++) {
                    int index = i;
                    LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.write(SOURCE.charAt(index)));
                    assertEquals(limit, exception.getLimit());
                    assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("write(char[], int, int)")
        void testWriteCharArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                char[] chars = SOURCE.toCharArray();
                int index = 0;
                while (index < chars.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    if (to > limit) {
                        int i = index;
                        LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.write(chars, i, to - i));
                        assertEquals(limit, exception.getLimit());
                        assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                    } else {
                        writer.write(chars, index, to - index);
                    }
                    index = to;
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("write(String, int, int)")
        void testWriteStringRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    if (to > limit) {
                        int i = index;
                        LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.write(SOURCE, i, to - i));
                        assertEquals(limit, exception.getLimit());
                        assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                    } else {
                        writer.write(SOURCE, index, to - index);
                    }
                    index = to;
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(CharSequence)")
        void testAppendCharSequence() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    if (to > limit) {
                        int i = index;
                        LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.append(SOURCE.substring(i, to)));
                        assertEquals(limit, exception.getLimit());
                        assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                    } else {
                        writer.append(SOURCE.substring(index, to));
                    }
                    index = to;
                }
            }
            assertEquals(expected, output.toString());

            // append null
            limit = 2;
            expected = "null".substring(0, limit);

            output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.append(null));
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        void testAppendCharSequenceRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    if (to > limit) {
                        int i = index;
                        LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.append(SOURCE, i, to));
                        assertEquals(limit, exception.getLimit());
                        assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                    } else {
                        writer.append(SOURCE, index, to);
                    }
                    index = to;
                }
            }
            assertEquals(expected, output.toString());

            // append null
            limit = 2;
            expected = "null".substring(0, limit);

            output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.append(null, 0, 3));
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("append(char)")
        void testAppendChar() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringWriter output = new StringWriter(expected.length());

            try (Writer writer = new LimitWriter(output, limit, THROW)) {
                for (int i = 0; i < limit; i++) {
                    writer.append(SOURCE.charAt(i));
                }
                for (int i = limit; i < SOURCE.length(); i++) {
                    int index = i;
                    LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.append(SOURCE.charAt(index)));
                    assertEquals(limit, exception.getLimit());
                    assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                }
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("flush()")
        void testFlush() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer writer = new LimitWriter(output, 1, THROW)) {
                writer.flush();
            }
            verify(output).flush();
            verify(output).close();
            verifyNoMoreInteractions(output);
        }
    }
}
