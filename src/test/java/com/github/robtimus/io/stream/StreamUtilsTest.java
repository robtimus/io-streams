/*
 * StreamUtilsTest.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkOffsetAndLength;
import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import static com.github.robtimus.io.stream.StreamUtils.dontClose;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@SuppressWarnings("nls")
class StreamUtilsTest extends TestBase {

    @TestFactory
    @DisplayName("dontClose(InputStream)")
    DynamicTest[] dontCloseInputStream() {
        return new DynamicTest[] {
                dynamicTest("non-null", () -> {
                    ByteArrayInputStream input = spy(new ByteArrayInputStream("Hello World".getBytes()));
                    byte[] array = new byte[2];
                    try (InputStream wrapper = dontClose(input)) {
                        assertEquals('H', wrapper.read());
                        assertEquals(2, wrapper.read(array));
                        assertArrayEquals(new byte[] { 'e', 'l', }, array);
                        assertEquals(2, wrapper.read(array, 0, 2));
                        assertArrayEquals(new byte[] { 'l', 'o', }, array);
                        assertTrue(wrapper.markSupported());
                        wrapper.mark(5);
                        assertEquals(5, wrapper.skip(5));
                        assertEquals(1, wrapper.available());
                        wrapper.reset();
                        assertEquals(' ', wrapper.read());
                    }
                    verify(input, never()).close();
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> dontClose((InputStream) null));
                }),
        };
    }

    @TestFactory
    @DisplayName("dontClose(OutputStream)")
    DynamicTest[] dontCloseOutputStream() {
        return new DynamicTest[] {
                dynamicTest("non-null", () -> {
                    ByteArrayOutputStream output = spy(new ByteArrayOutputStream());
                    try (OutputStream wrapper = dontClose(output)) {
                        wrapper.write('H');
                        wrapper.write("ello".getBytes());
                        wrapper.write("Hello World".getBytes(), 5, 6);
                        wrapper.flush();
                    }
                    assertEquals("Hello World", output.toString());
                    verify(output).flush();
                    verify(output, never()).close();
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> dontClose((OutputStream) null));
                }),
        };
    }

    @TestFactory
    @DisplayName("dontClose(Reader)")
    DynamicTest[] dontCloseReader() {
        return new DynamicTest[] {
                dynamicTest("non-null", () -> {
                    StringReader input = spy(new StringReader("Hello World"));
                    char[] array = new char[2];
                    try (Reader wrapper = dontClose(input)) {
                        assertEquals('H', wrapper.read());
                        assertEquals(2, wrapper.read(array));
                        assertArrayEquals(new char[] { 'e', 'l', }, array);
                        assertEquals(2, wrapper.read(array, 0, 2));
                        assertArrayEquals(new char[] { 'l', 'o', }, array);
                        assertTrue(wrapper.markSupported());
                        wrapper.mark(5);
                        assertEquals(5, wrapper.skip(5));
                        assertTrue(wrapper.ready());
                        wrapper.reset();
                        assertEquals(' ', wrapper.read());
                    }
                    verify(input, never()).close();
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> dontClose((Reader) null));
                }),
        };
    }

    @TestFactory
    @DisplayName("dontClose(Writer)")
    DynamicTest[] dontCloseWriter() {
        return new DynamicTest[] {
                dynamicTest("non-null", () -> {
                    StringWriter output = spy(new StringWriter());
                    try (Writer wrapper = dontClose(output)) {
                        wrapper.write('H');
                        wrapper.write("e".toCharArray());
                        wrapper.write("Hello World".toCharArray(), 2, 2);
                        wrapper.write("o ");
                        wrapper.write("Hello World", 6, 2);
                        wrapper.append('r');
                        wrapper.append("l");
                        wrapper.append("Hello World", 10, 11);
                        wrapper.flush();
                    }
                    assertEquals("Hello World", output.toString());
                    verify(output).flush();
                    verify(output, never()).close();
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> dontClose((InputStream) null));
                }),
        };
    }

    @Test
    @DisplayName("checkOffsetAndLength(byte[], int, int)")
    void testCheckOffsetAndLengthForByteArray() {
        byte[] array = SOURCE.getBytes();
        checkOffsetAndLength(array, 0, array.length);
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 0, array.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, array.length));
        checkOffsetAndLength(array, 1, 0);
    }

    @Test
    @DisplayName("checkOffsetAndLength(char[], int, int)")
    void testCheckOffsetAndLengthForCharArray() {
        char[] array = SOURCE.toCharArray();
        checkOffsetAndLength(array, 0, array.length);
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 0, array.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, array.length));
        checkOffsetAndLength(array, 1, 0);
    }

    @Test
    @DisplayName("checkOffsetAndLength(CharSequence, int, int)")
    void testCheckOffsetAndLengthForCharSequence() {
        CharSequence sequence = SOURCE;
        checkOffsetAndLength(sequence, 0, sequence.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, -1, sequence.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 0, sequence.length() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 1, sequence.length()));
        checkOffsetAndLength(sequence, 1, 0);
    }

    @Test
    @DisplayName("checkStartAndEnd(CharSequence, int, int)")
    void testCheckStartAndEndForCharSequence() {
        checkStartAndEnd(SOURCE, 0, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 0, SOURCE.length() + 1));
        checkStartAndEnd(SOURCE, 1, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, 0));
    }
}
