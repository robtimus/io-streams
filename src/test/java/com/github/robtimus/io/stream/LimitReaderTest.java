/*
 * LimitReaderTest.java
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.io.input.NullReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class LimitReaderTest extends TestBase {

    @Test
    @DisplayName("negative limit")
    void testNegativeLimit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new LimitReader(new NullReader(), -1));
        assertEquals("-1 < 0", exception.getMessage());
    }

    @Nested
    @DisplayName("0 limit")
    class ZeroLimit {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            String expected = "";

            StringBuilder output = new StringBuilder(0);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), 0)) {
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            String expected = "";

            StringBuilder output = new StringBuilder(0);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), 0)) {
                char[] buffer = new char[1024];
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            String expected = "";

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), 0)) {
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            try (Reader reader = new LimitReader(new StringReader(SOURCE), 0)) {
                assertFalse(reader.ready());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            String expected = "";

            StringBuilder output = new StringBuilder(0);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), 0)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                assertEquals(-1, reader.read(buffer));
                reader.reset();
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }
    }

    @Nested
    @DisplayName("Exceed limit with DISCARD strategy")
    class Discard {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                int c;
                while ((c = reader.read()) != -1) {
                    output.append((char) c);
                }
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                char[] buffer = new char[1024];
                final int offset = 100;
                int len;
                while ((len = reader.read(buffer, offset, 10)) != -1) {
                    output.append(buffer, 100, len);
                }
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(21, limit);

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                char[] data = new char[10];
                int len = reader.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals(10, reader.skip(10));
                assertEquals(1, reader.skip(1));
                IOUtils.copy(reader, output);
                assertEquals(0, reader.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length() / 2;

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                for (int i = 0; i < limit; i++) {
                    assertTrue(reader.ready());
                    reader.read();
                }
                assertFalse(reader.ready());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(expected.length());

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                int len;
                assertEquals(buffer.length, reader.read(buffer));
                output.append(buffer);
                reader.reset();
                while ((len = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(expected, output.toString());
        }
    }

    @Nested
    @DisplayName("Exceed limit with THROW strategy")
    class Throw {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit, THROW)) {
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    int c;
                    while ((c = reader.read()) != -1) {
                        output.append((char) c);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init.get(limit), exception.getMessage());
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit, THROW)) {
                char[] buffer = new char[1024];
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    final int offset = 100;
                    int len;
                    while ((len = reader.read(buffer, offset, 10)) != -1) {
                        output.append(buffer, 100, len);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init.get(limit), exception.getMessage());
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, 10);

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit, THROW)) {
                char[] data = new char[10];
                int len = reader.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals(10, reader.skip(10));
                assertEquals(1, reader.skip(1));
                // 21 characters have been read or skipped so far
                assertEquals(limit - 21, reader.skip(limit - 21));
                // the reader is now at its end
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> reader.skip(1));
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init.get(limit), exception.getMessage());
                assertEquals(0, reader.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length() / 2;

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit, THROW)) {
                for (int i = 0; i < limit; i++) {
                    assertTrue(reader.ready());
                    reader.read();
                }
                assertFalse(reader.ready());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() / 2;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit + 10);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit, THROW)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                assertEquals(buffer.length, reader.read(buffer));
                output.append(buffer);
                reader.reset();
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    int len;
                    while ((len = reader.read(buffer)) != -1) {
                        output.append(buffer, 0, len);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init.get(limit), exception.getMessage());
            }
            assertEquals(expected, output.toString());
        }
    }

    @Nested
    @DisplayName("limit == length")
    class LimitIsLength {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length();
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                int c;
                while ((c = reader.read()) != -1) {
                    output.append((char) c);
                }
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length();
            String expected = SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                char[] buffer = new char[1024];
                final int offset = 100;
                int len;
                while ((len = reader.read(buffer, offset, 10)) != -1) {
                    output.append(buffer, 100, len);
                }
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length();
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(21, limit);

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                char[] data = new char[10];
                int len = reader.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals(10, reader.skip(10));
                assertEquals(1, reader.skip(1));
                IOUtils.copy(reader, output);
                assertEquals(0, reader.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length();

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                for (int i = 0; i < limit; i++) {
                    assertTrue(reader.ready());
                    reader.read();
                }
                assertFalse(reader.ready());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length();
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(0, limit);

            StringBuilder output = new StringBuilder(limit + 10);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                int len;
                assertEquals(buffer.length, reader.read(buffer));
                output.append(buffer);
                reader.reset();
                while ((len = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(expected, output.toString());
        }
    }

    @Nested
    @DisplayName("limit == length + 1")
    class LimitIsLengthPlusOne {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() + 1;
            String expected = SOURCE;

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                int c;
                while ((c = reader.read()) != -1) {
                    output.append((char) c);
                }
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() + 1;
            String expected = SOURCE;

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                char[] buffer = new char[1024];
                final int offset = 100;
                int len;
                while ((len = reader.read(buffer, offset, 10)) != -1) {
                    output.append(buffer, 100, len);
                }
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() + 1;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(21);

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                char[] data = new char[10];
                int len = reader.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals(10, reader.skip(10));
                assertEquals(1, reader.skip(1));
                IOUtils.copy(reader, output);
                assertEquals(0, reader.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length() + 1;

            // use CharSequenceReader instead of StringReader since the latter always returns true for ready()
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    assertTrue(reader.ready());
                    reader.read();
                }
                assertFalse(reader.ready());
                assertEquals(-1, reader.read());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() + 1;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(0);

            StringBuilder output = new StringBuilder(SOURCE.length() + 10);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                int len;
                assertEquals(buffer.length, reader.read(buffer));
                output.append(buffer);
                reader.reset();
                while ((len = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(expected, output.toString());
        }
    }

    @Nested
    @DisplayName("limit > length")
    class LimitIsLargerThanLength {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() * 2;
            String expected = SOURCE;

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                int c;
                while ((c = reader.read()) != -1) {
                    output.append((char) c);
                }
                assertEquals(-1, reader.read());
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() * 2;
            String expected = SOURCE;

            StringBuilder output = new StringBuilder(limit);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                char[] buffer = new char[1024];
                final int offset = 100;
                int len;
                while ((len = reader.read(buffer, offset, 10)) != -1) {
                    output.append(buffer, 100, len);
                }
                assertEquals(-1, reader.read(buffer));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() * 2;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(21);

            StringWriter output = new StringWriter(expected.length());

            // use CharSequenceReader instead of StringReader since the latter doesn't check for negative skip values
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                char[] data = new char[10];
                int len = reader.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, reader.skip(0));
                assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals(10, reader.skip(10));
                assertEquals(1, reader.skip(1));
                IOUtils.copy(reader, output);
                assertEquals(0, reader.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length() * 2;

            // use CharSequenceReader instead of StringReader since the latter always returns true for ready()
            try (Reader reader = new LimitReader(new CharSequenceReader(SOURCE), limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    assertTrue(reader.ready());
                    reader.read();
                }
                assertFalse(reader.ready());
                assertEquals(-1, reader.read());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() * 2;
            String expected = SOURCE.substring(0, 10) + SOURCE.substring(0);

            StringBuilder output = new StringBuilder(SOURCE.length() + 10);

            try (Reader reader = new LimitReader(new StringReader(SOURCE), limit)) {
                assertTrue(reader.markSupported());
                reader.mark(10);
                char[] buffer = new char[10];
                int len;
                assertEquals(buffer.length, reader.read(buffer));
                output.append(buffer);
                reader.reset();
                while ((len = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(expected, output.toString());
        }
    }
}
