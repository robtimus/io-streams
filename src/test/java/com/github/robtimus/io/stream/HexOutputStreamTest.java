/*
 * HexOutputStreamTest.java
 * Copyright 2019 Rob Spoor
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

import static com.github.robtimus.io.stream.HexOutputStream.toHex;
import static com.github.robtimus.io.stream.TestData.SOURCE;
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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({ "javadoc", "nls" })
public class HexOutputStreamTest {

    private static final byte[] CAFEBABE = { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

    @Test
    @DisplayName("flush()")
    public void testFlush() throws IOException {
        StringWriter writer = spy(new StringWriter());
        try (HexOutputStream output = new HexOutputStream(writer)) {
            output.flush();
        }
        verify(writer).flush();
        verify(writer).close();
        verifyNoMoreInteractions(writer);
    }

    @Test
    @DisplayName("operations on closed stream")
    public void testOperationsOnClosedStream() throws IOException {
        @SuppressWarnings("resource")
        HexOutputStream output = new HexOutputStream(new StringBuilder());
        output.close();
        assertClosed(() -> output.write(CAFEBABE[0]));
        assertClosed(() -> output.write(CAFEBABE));
        assertClosed(() -> output.write(CAFEBABE, 0, 0));
        assertClosed(() -> output.flush());
        output.close();
    }

    private void assertClosed(Executable executable) {
        IOException thrown = assertThrows(IOException.class, executable);
        assertEquals(Messages.stream.closed.get(), thrown.getMessage());
    }

    @Nested
    @DisplayName("no case specified")
    public class NoCaseSpecified extends HexTest {

        public NoCaseSpecified() {
            super(HexOutputStream::new, "cafebabe");
        }
    }

    @Nested
    @DisplayName("lower case")
    public class LowerCase extends HexTest {

        public LowerCase() {
            super(a -> new HexOutputStream(a, false), "cafebabe");
        }
    }

    @Nested
    @DisplayName("upper case")
    public class UpperCase extends HexTest {

        public UpperCase() {
            super(a -> new HexOutputStream(a, true), "CAFEBABE");
        }
    }

    private abstract static class HexTest {

        private final Function<Appendable, HexOutputStream> constructor;
        private final String cafebabe;

        private HexTest(Function<Appendable, HexOutputStream> constructor, String cafebabe) {
            this.constructor = constructor;
            this.cafebabe = cafebabe;
        }

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException {
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
        public void testWriteByteArray() throws IOException {
            StringBuilder appendable = new StringBuilder();
            try (HexOutputStream output = constructor.apply(appendable)) {
                output.write(CAFEBABE);
            }
            assertEquals(cafebabe, appendable.toString());
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        public void testWriteByteArrayRange() throws IOException {
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

            // write in bulk
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
    @DisplayName("toHex(byte[]")
    public void testToHex() {
        assertEquals("cafebabe", toHex(CAFEBABE));
        assertThrows(NullPointerException.class, () -> toHex(null));
    }

    @Test
    @DisplayName("toHex(byte[], int, int)")
    public void testRangeToHex() {
        byte[] bytes = new byte[CAFEBABE.length + 2];
        System.arraycopy(CAFEBABE, 0, bytes, 1, CAFEBABE.length);
        assertEquals("cafebabe", toHex(bytes, 1, CAFEBABE.length + 1));
        assertThrows(NullPointerException.class, () -> toHex(null));
        assertThrows(IndexOutOfBoundsException.class, () -> toHex(CAFEBABE, -1, CAFEBABE.length));
        assertThrows(IndexOutOfBoundsException.class, () -> toHex(CAFEBABE, 0, CAFEBABE.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> toHex(CAFEBABE, 1, 0));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "false, cafebabe",
        "true, CAFEBABE"
    })
    @DisplayName("toHex(byte[], boolean)")
    public void testToHex(boolean upperCase, String expected) {
        assertEquals(expected, toHex(CAFEBABE, upperCase));
        assertThrows(NullPointerException.class, () -> toHex(null, upperCase));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "false, cafebabe",
        "true, CAFEBABE"
    })
    @DisplayName("toHex(byte[], int, int, boolean)")
    public void testRangeToHex(boolean upperCase, String expected) {
        byte[] bytes = new byte[CAFEBABE.length + 2];
        System.arraycopy(CAFEBABE, 0, bytes, 1, CAFEBABE.length);
        assertEquals(expected, toHex(bytes, 1, CAFEBABE.length + 1, upperCase));
        assertThrows(NullPointerException.class, () -> toHex(null, upperCase));
    }
}
