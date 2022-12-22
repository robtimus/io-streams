/*
 * LimitInputStreamTest.java
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class LimitInputStreamTest extends TestBase {

    @Test
    @DisplayName("negative limit")
    void testNegativeLimit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new LimitInputStream(new NullInputStream(), -1));
        assertEquals("-1 < 0", exception.getMessage());
    }

    @Nested
    @DisplayName("0 limit")
    class ZeroLimit {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            byte[] bytes = {};
            byte[] expected = {};

            ByteArrayOutputStream output = new ByteArrayOutputStream(0);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), 0)) {
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            byte[] bytes = {};
            byte[] expected = {};

            ByteArrayOutputStream output = new ByteArrayOutputStream(0);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), 0)) {
                byte[] buffer = new byte[1024];
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            byte[] bytes = {};
            byte[] expected = {};

            ByteArrayOutputStream output = new ByteArrayOutputStream(0);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), 0)) {
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        void testAvailable() throws IOException {
            byte[] bytes = {};

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), 0)) {
                assertEquals(0, input.available());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            byte[] bytes = {};
            byte[] expected = {};

            ByteArrayOutputStream output = new ByteArrayOutputStream(0);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), 0)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                assertEquals(-1, input.read(buffer));
                input.reset();
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }

    @Nested
    @DisplayName("Exceed limit with DISCARD strategy")
    class Discard {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write(c);
                }
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] buffer = new byte[1024];
                final int offset = 100;
                int len;
                while ((len = input.read(buffer, offset, 10)) != -1) {
                    output.write(buffer, 100, len);
                }
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(21, limit)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(10, input.skip(10));
                assertEquals(1, input.skip(1));
                IOUtils.copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("ready()")
        void testReady() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                for (int i = 0; i < limit; i++) {
                    assertEquals(limit - i, input.available());
                    input.read();
                }
                assertEquals(0, input.available());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(0, limit)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                int len;
                assertEquals(buffer.length, input.read(buffer));
                output.write(buffer);
                input.reset();
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }

    @Nested
    @DisplayName("Exceed limit with THROW strategy")
    class Throw {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit, THROW)) {
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    int c;
                    while ((c = input.read()) != -1) {
                        output.write((byte) c);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit, THROW)) {
                byte[] buffer = new byte[1024];
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    final int offset = 100;
                    int len;
                    while ((len = input.read(buffer, offset, 10)) != -1) {
                        output.write(buffer, 100, len);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, 10).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit, THROW)) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(10, input.skip(10));
                assertEquals(1, input.skip(1));
                // 21 bytes have been read or skipped so far
                assertEquals(limit - 21, input.skip(limit - 21));
                // the input is now at its end
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> input.skip(1));
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        void testAvailable() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit, THROW)) {
                for (int i = 0; i < limit; i++) {
                    assertEquals(limit - i, input.available());
                    input.read();
                }
                assertEquals(0, input.available());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(0, limit)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit, THROW)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                assertEquals(buffer.length, input.read(buffer));
                output.write(buffer);
                input.reset();
                LimitExceededException exception = assertThrows(LimitExceededException.class, () -> {
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                });
                assertEquals(limit, exception.getLimit());
                assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }

    @Nested
    @DisplayName("limit == length")
    class LimitIsLength {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length();
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write((byte) c);
                }
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length();
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] buffer = new byte[1024];
                final int offset = 100;
                int len;
                while ((len = input.read(buffer, offset, 10)) != -1) {
                    output.write(buffer, 100, len);
                }
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length();
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(21, limit)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(10, input.skip(10));
                assertEquals(1, input.skip(1));
                IOUtils.copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        void testAvailable() throws IOException {
            int limit = SOURCE.length();
            byte[] bytes = SOURCE.getBytes();

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                for (int i = 0; i < limit; i++) {
                    assertEquals(limit - i, input.available());
                    input.read();
                }
                assertEquals(0, input.available());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length();
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(0, limit)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                int len;
                assertEquals(buffer.length, input.read(buffer));
                output.write(buffer);
                input.reset();
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }

    @Nested
    @DisplayName("limit == length + 1")
    class LimitIsLengthPlusOne {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() + 1;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write((byte) c);
                }
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() + 1;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] buffer = new byte[1024];
                final int offset = 100;
                int len;
                while ((len = input.read(buffer, offset, 10)) != -1) {
                    output.write(buffer, 100, len);
                }
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() + 1;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(21)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(10, input.skip(10));
                assertEquals(1, input.skip(1));
                IOUtils.copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        void testAvailable() throws IOException {
            int limit = SOURCE.length() + 1;
            byte[] bytes = SOURCE.getBytes();

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    assertEquals(SOURCE.length() - i, input.available());
                    input.read();
                }
                assertEquals(0, input.available());
                assertEquals(-1, input.read());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() + 1;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(0)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                int len;
                assertEquals(buffer.length, input.read(buffer));
                output.write(buffer);
                input.reset();
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }

    @Nested
    @DisplayName("limit > length")
    class LimitIsLargerThanLength {

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            int limit = SOURCE.length() * 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write((byte) c);
                }
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArrayRange() throws IOException {
            int limit = SOURCE.length() * 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] buffer = new byte[1024];
                final int offset = 100;
                int len;
                while ((len = input.read(buffer, offset, 10)) != -1) {
                    output.write(buffer, 100, len);
                }
                assertEquals(-1, input.read(buffer));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            int limit = SOURCE.length() * 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(21)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(10, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(10, input.skip(10));
                assertEquals(1, input.skip(1));
                IOUtils.copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        void testAvailable() throws IOException {
            int limit = SOURCE.length() * 2;
            byte[] bytes = SOURCE.getBytes();

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    assertEquals(SOURCE.length() - i, input.available());
                    input.read();
                }
                assertEquals(0, input.available());
                assertEquals(-1, input.read());
            }
        }

        @Test
        @DisplayName("mark(int) and reset")
        void testMarkAndReset() throws IOException {
            int limit = SOURCE.length() * 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = (SOURCE.substring(0, 10) + SOURCE.substring(0)).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (InputStream input = new LimitInputStream(new ByteArrayInputStream(bytes), limit)) {
                assertTrue(input.markSupported());
                input.mark(10);
                byte[] buffer = new byte[10];
                int len;
                assertEquals(buffer.length, input.read(buffer));
                output.write(buffer);
                input.reset();
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }
    }
}
