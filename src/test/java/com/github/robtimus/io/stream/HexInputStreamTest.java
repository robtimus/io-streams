/*
 * HexInputStreamTest.java
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

import static com.github.robtimus.io.stream.HexInputStream.decode;
import static com.github.robtimus.io.stream.HexInputStream.tryDecode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class HexInputStreamTest extends TestBase {

    private static final byte[] CAFEBABE_BYTES = { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };
    private static final String CAFEBABE_STRING = "CAFEBABE";

    @Nested
    @DisplayName("read()")
    class ReadByte {

        @Test
        @DisplayName("valid hex")
        void testValidHex() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(CAFEBABE_BYTES.length);

            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING))) {
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            }
            assertArrayEquals(CAFEBABE_BYTES, output.toByteArray());
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() throws IOException {
            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING + "A"))) {
                IOException exception = assertThrows(IOException.class, () -> {
                    while (input.read() != -1) {
                        // ignore
                    }
                });
                assertEquals(Messages.hex.eof(), exception.getMessage());

                exception = assertThrows(IOException.class, input::read);
                assertEquals(Messages.hex.eof(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() throws IOException {
            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING + "XA"))) {
                IOException exception = assertThrows(IOException.class, () -> {
                    while (input.read() != -1) {
                        // ignore
                    }
                });
                assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());

                exception = assertThrows(IOException.class, input::read);
                assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("read(byte[], int, int)")
    class ReadByteArrayRange {

        @Test
        @DisplayName("valid hex")
        void testValidHex() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(CAFEBABE_BYTES.length);

            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING))) {
                assertEquals(0, input.read(new byte[5], 0, 0));
                assertEquals(0, output.size());
                IOUtils.copy(input, output, 5);
            }
            assertArrayEquals(CAFEBABE_BYTES, output.toByteArray());

            output.reset();
            try (HexInputStream input = new HexInputStream(new SlowReader(CAFEBABE_STRING))) {
                assertEquals(0, input.read(new byte[5], 0, 0));
                assertEquals(0, output.size());
                IOUtils.copy(input, output, 5);
            }
            assertArrayEquals(CAFEBABE_BYTES, output.toByteArray());

            // read using a huge array
            output.reset();
            byte[] expected = new byte[CAFEBABE_BYTES.length * 1000];
            StringBuilder content = new StringBuilder(CAFEBABE_STRING.length() * 1000);
            for (int i = 0, j = 0; i < 1000; i++, j += CAFEBABE_BYTES.length) {
                content.append(CAFEBABE_STRING);
                System.arraycopy(CAFEBABE_BYTES, 0, expected, j, CAFEBABE_BYTES.length);
            }
            try (HexInputStream input = new HexInputStream(new CharSequenceReader(content))) {
                byte[] buffer = new byte[expected.length];
                assertEquals(buffer.length, input.read(buffer));
                assertArrayEquals(expected, buffer);
            }
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() throws IOException {
            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING + "A"))) {
                byte[] buffer = new byte[5];
                IOException exception = assertThrows(IOException.class, () -> {
                    while (input.read(buffer) != -1) {
                        // ignore
                    }
                });
                assertEquals(Messages.hex.eof(), exception.getMessage());

                exception = assertThrows(IOException.class, () -> input.read(buffer));
                assertEquals(Messages.hex.eof(), exception.getMessage());
            }

            try (HexInputStream input = new HexInputStream(new SlowReader(CAFEBABE_STRING + "A"))) {
                byte[] buffer = new byte[5];
                IOException exception = assertThrows(IOException.class, () -> {
                    while (input.read(buffer) != -1) {
                        // ignore
                    }
                });
                assertEquals(Messages.hex.eof(), exception.getMessage());

                exception = assertThrows(IOException.class, () -> input.read(buffer));
                assertEquals(Messages.hex.eof(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() throws IOException {
            try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING + "XA"))) {
                byte[] buffer = new byte[5];
                IOException exception = assertThrows(IOException.class, () -> {
                    while (input.read(buffer) != -1) {
                        // ignore
                    }
                });
                assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());

                exception = assertThrows(IOException.class, () -> input.read(buffer));
                assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());
            }
        }
    }

    @Test
    @DisplayName("available()")
    void testAvailable() throws IOException {
        try (HexInputStream input = new HexInputStream(new CharSequenceReader(CAFEBABE_STRING))) {
            assertEquals(0, input.available());
            while (input.read() != -1) {
                assertEquals(0, input.available());
            }
            assertEquals(0, input.available());
        }
    }

    @Test
    @DisplayName("close()")
    void testClose() throws IOException {
        @SuppressWarnings("resource")
        Reader reader = mock(Reader.class);
        try (HexInputStream input = new HexInputStream(reader)) {
            // don't do anything
        }
        verify(reader).close();
        verifyNoMoreInteractions(reader);
    }

    @Test
    @DisplayName("operations on closed stream")
    void testOperationsOnClosedStream() throws IOException {
        @SuppressWarnings("resource")
        HexInputStream input = new HexInputStream(new CharSequenceReader(""));
        input.close();
        assertClosed(input::read);
        assertClosed(() -> input.read(new byte[0]));
        assertClosed(() -> input.read(new byte[0], 0, 0));
        assertClosed(input::available);
        input.close();
    }

    @Nested
    @DisplayName("decode(CharSequence)")
    class Decode {

        @Test
        @DisplayName("null")
        void testNull() {
            assertThrows(NullPointerException.class, () -> decode(null));
        }

        @Test
        @DisplayName("valid hex")
        void testValidHex() {
            assertArrayEquals(CAFEBABE_BYTES, decode(CAFEBABE_STRING));
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> decode(CAFEBABE_STRING + "A"));
            assertEquals(Messages.hex.eof(), exception.getMessage());
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> decode(CAFEBABE_STRING + "XA"));
            assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("decode(CharSequence, int, int)")
    class DecodeRange {

        @Test
        @DisplayName("null")
        void testNull() {
            assertThrows(NullPointerException.class, () -> decode(null, 0, 0));
        }

        @Test
        @DisplayName("invalid indexes")
        void testInvalidIndexes() {
            int length = CAFEBABE_STRING.length();
            assertThrows(IndexOutOfBoundsException.class, () -> decode(CAFEBABE_STRING, -1, length));
            assertThrows(IndexOutOfBoundsException.class, () -> decode(CAFEBABE_STRING, 0, length + 1));
            assertThrows(IndexOutOfBoundsException.class, () -> decode(CAFEBABE_STRING, 1, 0));
        }

        @Test
        @DisplayName("valid hex")
        void testValidHex() {
            assertArrayEquals(CAFEBABE_BYTES, decode("x" + CAFEBABE_STRING + "x", 1, CAFEBABE_STRING.length() + 1));
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() {
            int length = CAFEBABE_STRING.length();
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> decode("x" + CAFEBABE_STRING + "Ax", 1, length + 2));
            assertEquals(Messages.hex.eof(), exception.getMessage());
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() {
            int length = CAFEBABE_STRING.length();
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> decode("x" + CAFEBABE_STRING + "XAx", 1, length + 3));
            assertEquals(Messages.hex.invalidChar('X'), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("tryDecode(CharSequence)")
    class TryDecode {

        @Test
        @DisplayName("null")
        void testNull() {
            assertEquals(Optional.empty(), tryDecode(null));
        }

        @Test
        @DisplayName("valid hex")
        void testValidHex() {
            Optional<byte[]> result = tryDecode(CAFEBABE_STRING);
            assertNotEquals(Optional.empty(), result);
            assertArrayEquals(CAFEBABE_BYTES, result.get());
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() {
            assertEquals(Optional.empty(), tryDecode(CAFEBABE_STRING + "A"));
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() {
            assertEquals(Optional.empty(), tryDecode(CAFEBABE_STRING + "XA"));
        }
    }

    @Nested
    @DisplayName("tryDecode(CharSequence, int, int)")
    class TryDecodeRange {

        @Test
        @DisplayName("null")
        void testNull() {
            assertEquals(Optional.empty(), tryDecode(null, 0, 0));
        }

        @Test
        @DisplayName("invalid indexes")
        void testInvalidIndexes() {
            int length = CAFEBABE_STRING.length();
            assertThrows(IndexOutOfBoundsException.class, () -> tryDecode(CAFEBABE_STRING, -1, length));
            assertThrows(IndexOutOfBoundsException.class, () -> tryDecode(CAFEBABE_STRING, 0, length + 1));
            assertThrows(IndexOutOfBoundsException.class, () -> tryDecode(CAFEBABE_STRING, 1, 0));
        }

        @Test
        @DisplayName("valid hex")
        void testValidHex() {
            Optional<byte[]> result = tryDecode("x" + CAFEBABE_STRING + "x", 1, CAFEBABE_STRING.length() + 1);
            assertNotEquals(Optional.empty(), result);
            assertArrayEquals(CAFEBABE_BYTES, result.get());
        }

        @Test
        @DisplayName("odd length hex")
        void testOddLengthHex() {
            assertEquals(Optional.empty(), tryDecode("x" + CAFEBABE_STRING + "Ax", 1, CAFEBABE_STRING.length() + 2));
        }

        @Test
        @DisplayName("invalid hex")
        void testInvalidHex() {
            assertEquals(Optional.empty(), tryDecode("x" + CAFEBABE_STRING + "XAx", 1, CAFEBABE_STRING.length() + 3));
        }
    }

    private static final class SlowReader extends Reader {

        private final CharSequence source;
        private int index;
        private int increment;

        private SlowReader(CharSequence source) {
            this.source = source;
            index = 0;
            increment = 1;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (index >= source.length()) {
                return -1;
            }
            int oldIndex = index;
            for (int i = off, j = 0, k = 0; j < len && k < increment && index < source.length(); i++, j++, k++) {
                cbuf[i] = source.charAt(index++);
            }
            increment = 2;
            return index - oldIndex;
        }

        @Override
        public void close() throws IOException {
            // does nothing
        }
    }
}
