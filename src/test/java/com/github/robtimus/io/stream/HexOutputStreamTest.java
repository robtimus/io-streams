/*
 * HexOutputStreamTest.java
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

import static com.github.robtimus.io.stream.HexOutputStream.encode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("nls")
class HexOutputStreamTest extends TestBase {

    private static final byte[] CAFEBABE = { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

    @Test
    @DisplayName("flush()")
    void testFlush() throws IOException {
        StringWriter writer = spy(new StringWriter());
        try (HexOutputStream output = new HexOutputStream(writer)) {
            output.flush();
        }
        verify(writer).flush();
        verify(writer).close();
        verifyNoMoreInteractions(writer);
    }

    @Test
    @DisplayName("close()")
    void testClose() throws IOException {
        StringWriter writer = spy(new StringWriter());
        try (HexOutputStream outut = new HexOutputStream(writer)) {
            // don't do anything
        }
        verify(writer).close();
        verifyNoMoreInteractions(writer);
    }

    @Test
    @DisplayName("operations on closed stream")
    void testOperationsOnClosedStream() throws IOException {
        @SuppressWarnings("resource")
        HexOutputStream output = new HexOutputStream(new StringBuilder());
        output.close();
        assertClosed(() -> output.write(CAFEBABE[0]));
        assertClosed(() -> output.write(CAFEBABE));
        assertClosed(() -> output.write(CAFEBABE, 0, 0));
        assertClosed(output::flush);
        output.close();
    }

    @Nested
    @DisplayName("no case specified")
    class NoCaseSpecified extends HexTest {

        NoCaseSpecified() {
            super(HexOutputStream::new, "cafebabe");
        }
    }

    @Nested
    @DisplayName("lower case")
    class LowerCase extends HexTest {

        LowerCase() {
            super(a -> new HexOutputStream(a, false), "cafebabe");
        }
    }

    @Nested
    @DisplayName("upper case")
    class UpperCase extends HexTest {

        UpperCase() {
            super(a -> new HexOutputStream(a, true), "CAFEBABE");
        }
    }

    abstract static class HexTest {

        private final Function<Appendable, HexOutputStream> constructor;
        private final String cafebabe;

        private HexTest(Function<Appendable, HexOutputStream> constructor, String cafebabe) {
            this.constructor = constructor;
            this.cafebabe = cafebabe;
        }

        @Test
        @DisplayName("write(int)")
        void testWriteInt() throws IOException {
            StringBuilder appendable = new StringBuilder();
            try (HexOutputStream output = constructor.apply(appendable)) {
                for (byte b : CAFEBABE) {
                    output.write(b);
                }
            }
            assertEquals(cafebabe, appendable.toString());
        }

        @Test
        @DisplayName("write(byte[])")
        void testWriteByteArray() throws IOException {
            StringBuilder appendable = new StringBuilder();
            try (HexOutputStream output = constructor.apply(appendable)) {
                output.write(CAFEBABE);
            }
            assertEquals(cafebabe, appendable.toString());
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        void testWriteByteArrayRange() throws IOException {
            StringBuilder appendable = new StringBuilder();
            try (HexOutputStream output = constructor.apply(appendable)) {
                int index = 0;
                while (index < CAFEBABE.length) {
                    int to = Math.min(index + 2, SOURCE.length());
                    output.write(CAFEBABE, index, to - index);
                    index = to;
                }
            }
            assertEquals(cafebabe, appendable.toString());

            // write a huge array
            appendable.delete(0, appendable.length());
            StringBuilder expected = new StringBuilder(cafebabe.length() * 1000);
            try (HexOutputStream output = constructor.apply(appendable)) {
                byte[] content = new byte[CAFEBABE.length * 1000];
                for (int i = 0, j = 0; i < 1000; i++, j += CAFEBABE.length) {
                    expected.append(cafebabe);
                    System.arraycopy(CAFEBABE, 0, content, j, CAFEBABE.length);
                }
                output.write(content, 0, content.length);
            }
            assertEquals(expected.toString(), appendable.toString());
        }
    }

    @Test
    @DisplayName("encode(byte[]")
    void testEncode() {
        assertEquals("cafebabe", encode(CAFEBABE));
        assertThrows(NullPointerException.class, () -> encode(null));
    }

    @Test
    @DisplayName("encode(byte[], int, int)")
    void testEncodeRange() {
        byte[] bytes = new byte[CAFEBABE.length + 2];
        System.arraycopy(CAFEBABE, 0, bytes, 1, CAFEBABE.length);
        assertEquals("cafebabe", encode(bytes, 1, CAFEBABE.length + 1));
        assertThrows(NullPointerException.class, () -> encode(null));
        assertThrows(IndexOutOfBoundsException.class, () -> encode(CAFEBABE, -1, CAFEBABE.length));
        assertThrows(IndexOutOfBoundsException.class, () -> encode(CAFEBABE, 0, CAFEBABE.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> encode(CAFEBABE, 1, 0));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "false, cafebabe",
            "true, CAFEBABE"
    })
    @DisplayName("encode(byte[], boolean)")
    void testEncode(boolean upperCase, String expected) {
        assertEquals(expected, encode(CAFEBABE, upperCase));
        assertThrows(NullPointerException.class, () -> encode(null, upperCase));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "false, cafebabe",
            "true, CAFEBABE"
    })
    @DisplayName("encode(byte[], int, int, boolean)")
    void testEncodeRange(boolean upperCase, String expected) {
        byte[] bytes = new byte[CAFEBABE.length + 2];
        System.arraycopy(CAFEBABE, 0, bytes, 1, CAFEBABE.length);
        assertEquals(expected, encode(bytes, 1, CAFEBABE.length + 1, upperCase));
        assertThrows(NullPointerException.class, () -> encode(null, upperCase));
    }
}
