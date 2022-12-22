/*
 * LimitOutputStreamTest.java
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class LimitOutputStreamTest extends TestBase {

    @Test
    @DisplayName("negative limit")
    void testNegativeLimit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LimitOutputStream(NullOutputStream.NULL_OUTPUT_STREAM, -1));
        assertEquals("-1 < 0", exception.getMessage());
    }

    @Nested
    @DisplayName("Exceed limit with DISCARD strategy")
    class Discard {

        @Test
        @DisplayName("write(int)")
        void testWriteInt() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (OutputStream wrapper = new LimitOutputStream(output, limit)) {
                for (byte b : bytes) {
                    wrapper.write(b);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        void testWriteByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (OutputStream writer = new LimitOutputStream(output, limit)) {
                int index = 0;
                while (index < bytes.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    writer.write(bytes, index, to - index);
                    index = to;
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("flush()")
        void testFlush() throws IOException {
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream(SOURCE.length()));

            try (OutputStream writer = new LimitOutputStream(output, 1)) {
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
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (OutputStream writer = new LimitOutputStream(output, limit, THROW)) {
                for (int i = 0; i < limit; i++) {
                    writer.write(bytes[i]);
                }
                for (int i = limit; i < SOURCE.length(); i++) {
                    int index = i;
                    LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.write(bytes[index]));
                    assertEquals(limit, exception.getLimit());
                    assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        void testWriteByteArrayRange() throws IOException {
            int limit = SOURCE.length() / 2;
            byte[] bytes = SOURCE.getBytes();
            byte[] expected = SOURCE.substring(0, limit).getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            try (OutputStream writer = new LimitOutputStream(output, limit, THROW)) {
                int index = 0;
                while (index < bytes.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    if (to > limit) {
                        int i = index;
                        LimitExceededException exception = assertThrows(LimitExceededException.class, () -> writer.write(bytes, i, to - i));
                        assertEquals(limit, exception.getLimit());
                        assertEquals(Messages.LimitExceededException.init(limit), exception.getMessage());
                    } else {
                        writer.write(bytes, index, to - index);
                    }
                    index = to;
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("flush()")
        void testFlush() throws IOException {
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream(SOURCE.length()));

            try (OutputStream writer = new LimitOutputStream(output, 1, THROW)) {
                writer.flush();
            }
            verify(output).flush();
            verify(output).close();
            verifyNoMoreInteractions(output);
        }
    }
}
