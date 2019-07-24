/*
 * StreamUtilsTest.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkOffsetAndLength;
import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import static com.github.robtimus.io.stream.StreamUtils.dontClose;
import static com.github.robtimus.io.stream.StreamUtils.writer;
import static com.github.robtimus.io.stream.TestData.SOURCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@SuppressWarnings({ "javadoc", "nls" })
public class StreamUtilsTest {

    @TestFactory
    @DisplayName("writer(Appendable)")
    public DynamicTest[] testWriter() {
        return new DynamicTest[] {
                dynamicTest("Writer", () -> {
                    Writer writer = new StringWriter();
                    assertSame(writer, writer(writer));
                }),
                dynamicTest("StringBuilder", () -> {
                    StringBuilder sb = new StringBuilder();
                    @SuppressWarnings("resource")
                    Writer writer = writer(sb);
                    assertThat(writer, instanceOf(AppendableWriter.class));
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> writer(null));
                }),
        };
    }

    @Nested
    @DisplayName("dontClose(InputStream)")
    public class DontCloseInputStream {

        @Test
        @DisplayName("read()")
        public void testReadByte() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

            try (InputStream wrapped = dontClose(input)) {
                int b;
                while ((b = wrapped.read()) != -1) {
                    output.write(b);
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(input, times(bytes.length + 1)).read();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("read(byte[])")
        public void testReadByteArray() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

            try (InputStream wrapped = dontClose(input)) {
                byte[] buffer = new byte[10];
                int len;
                while ((len = wrapped.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(input, atLeastOnce()).read(any());
            verify(input, never()).close();
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        public void testReadByteArrayRange() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

            try (InputStream wrapped = dontClose(input)) {
                byte[] buffer = new byte[1024];
                final int offset = 100;
                int len;
                while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                    output.write(buffer, 100, len);
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(input, atLeastOnce()).read(any(), anyInt(), anyInt());
            verify(input, never()).close();
        }

        @Test
        @DisplayName("skip(long)")
        public void testSkip() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));

            try (InputStream wrapped = dontClose(input)) {
                assertEquals(bytes.length, wrapped.skip(Integer.MAX_VALUE));
                assertEquals(-1, wrapped.read());
            }
            verify(input, times(1)).skip(anyLong());
            verify(input, never()).close();
        }

        @Test
        @DisplayName("available()")
        public void testAvailable() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));

            try (InputStream wrapped = dontClose(input)) {
                assertEquals(input.available(), wrapped.available());
            }
            verify(input, times(2)).available();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("mark(int) and reset")
        public void testMarkAndReset() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

            try (InputStream wrapped = dontClose(input)) {
                assertEquals(input.markSupported(), wrapped.markSupported());
                wrapped.mark(10);
                byte[] buffer = new byte[10];
                int len;
                wrapped.read(buffer);
                wrapped.reset();
                while ((len = wrapped.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(input, times(2)).markSupported();
            verify(input, times(1)).mark(anyInt());
            verify(input, times(1)).reset();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("toString()")
        public void testToString() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);

            try (InputStream wrapped = dontClose(input)) {
                assertThat(wrapped.toString(), not(containsString("StreamUtils$")));
                assertEquals(StreamUtils.class.getName() + "#dontClose(" + input + ")", wrapped.toString());
            }
        }
    }

    @Nested
    @DisplayName("dontClose(OutputStream)")
    public class DontCloseOutputStream {

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream(bytes.length));

            try (OutputStream wrapped = dontClose(output)) {
                for (byte b : bytes) {
                    wrapped.write(b);
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(output, atLeastOnce()).write(anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(byte[])")
        public void testWriteByteArray() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream(bytes.length));

            try (OutputStream wrapped = dontClose(output)) {
                wrapped.write(bytes);
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(output, atLeastOnce()).write(any());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        public void testWriteByteArrayRange() throws IOException {
            byte[] bytes = SOURCE.getBytes();
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream(bytes.length));

            try (OutputStream wrapped = dontClose(output)) {
                int index = 0;
                while (index < bytes.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    wrapped.write(bytes, index, to - index);
                    index = to;
                }
            }
            assertArrayEquals(bytes, output.toByteArray());
            verify(output, atLeastOnce()).write(any(), anyInt(), anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("flush()")
        public void testFlush() throws IOException {
            ByteArrayOutputStream output = spy(new ByteArrayOutputStream());

            try (OutputStream wrapped = dontClose(output)) {
                wrapped.flush();
            }
            verify(output, times(1)).flush();
            verify(output, never()).close();
        }

        @Test
        @DisplayName("toString()")
        public void testToString() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            try (OutputStream wrapped = dontClose(output)) {
                assertThat(wrapped.toString(), not(containsString("StreamUtils$")));
                assertEquals(StreamUtils.class.getName() + "#dontClose(" + output + ")", wrapped.toString());
            }
        }
    }

    @Nested
    @DisplayName("dontClose(Reader)")
    public class DontCloseReader {

        @Test
        @DisplayName("read(CharBuffer)")
        public void testReadCharBuffer() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));
            StringBuilder output = new StringBuilder(SOURCE.length());

            try (Reader wrapped = dontClose(input)) {
                CharBuffer buffer = CharBuffer.allocate(10);
                while (wrapped.read(buffer) != -1) {
                    buffer.flip();
                    output.append(buffer);
                }
                assertEquals(SOURCE, output.toString());
                verify(input, atLeastOnce()).read(any(CharBuffer.class));
                verify(input, never()).close();
            }
        }

        @Test
        @DisplayName("read()")
        public void testReadChar() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));
            StringBuilder output = new StringBuilder(SOURCE.length());

            try (Reader wrapped = dontClose(input)) {
                int c;
                while ((c = wrapped.read()) != -1) {
                    output.append((char) c);
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(input, atLeastOnce()).read();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("read(char[])")
        public void testReadCharArray() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));
            StringBuilder output = new StringBuilder(SOURCE.length());

            try (Reader wrapped = dontClose(input)) {
                char[] buffer = new char[10];
                int len;
                while ((len = wrapped.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(input, atLeastOnce()).read(any(char[].class));
            verify(input, never()).close();
        }

        @Test
        @DisplayName("read(char[], int, int)")
        public void testReadByteArrayRange() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));
            StringBuilder output = new StringBuilder(SOURCE.length());

            try (Reader wrapped = dontClose(input)) {
                char[] buffer = new char[1024];
                final int offset = 100;
                int len;
                while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                    output.append(buffer, 100, len);
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(input, atLeastOnce()).read(any(), anyInt(), anyInt());
            verify(input, never()).close();
        }

        @Test
        @DisplayName("skip(long)")
        public void testSkip() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));

            try (Reader wrapped = dontClose(input)) {
                assertEquals(SOURCE.length(), wrapped.skip(Integer.MAX_VALUE));
                assertEquals(-1, wrapped.read());
            }
            verify(input, times(1)).skip(anyLong());
            verify(input, never()).close();
        }

        @Test
        @DisplayName("ready()")
        public void testReady() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));

            try (Reader wrapped = dontClose(input)) {
                assertEquals(input.ready(), wrapped.ready());
            }
            verify(input, times(2)).ready();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("mark(int) and reset")
        public void testMarkAndReset() throws IOException {
            StringReader input = spy(new StringReader(SOURCE));
            StringBuilder output = new StringBuilder(SOURCE.length());

            try (Reader wrapped = dontClose(input)) {
                assertEquals(input.markSupported(), wrapped.markSupported());
                wrapped.mark(10);
                char[] buffer = new char[10];
                int len;
                wrapped.read(buffer);
                wrapped.reset();
                while ((len = wrapped.read(buffer)) != -1) {
                    output.append(buffer, 0, len);
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(input, times(2)).markSupported();
            verify(input, times(1)).mark(anyInt());
            verify(input, times(1)).reset();
            verify(input, never()).close();
        }

        @Test
        @DisplayName("toString()")
        public void testToString() throws IOException {
            StringReader input = new StringReader(SOURCE);

            try (Reader wrapped = dontClose(input)) {
                assertThat(wrapped.toString(), not(containsString("StreamUtils$")));
                assertEquals(StreamUtils.class.getName() + "#dontClose(" + input + ")", wrapped.toString());
            }
        }
    }

    @Nested
    @DisplayName("dontClose(Writer)")
    public class DontCloseWriter {

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    wrapped.write(SOURCE.charAt(i));
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).write(anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(char[])")
        public void testWriteCharArray() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                wrapped.write(SOURCE.toCharArray());
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).write(any(char[].class));
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(char[], int, int)")
        public void testWriteCharArrayRange() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                char[] chars = SOURCE.toCharArray();
                int index = 0;
                while (index < chars.length) {
                    int to = Math.min(index + 5, SOURCE.length());
                    wrapped.write(chars, index, to - index);
                    index = to;
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).write(any(char[].class), anyInt(), anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(String)")
        public void testWriteString() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                wrapped.write(SOURCE);
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).write(any(String.class));
            verify(output, never()).close();
        }

        @Test
        @DisplayName("write(String, int, int)")
        public void testWriteStringRange() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    wrapped.write(SOURCE, index, to - index);
                    index = to;
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).write(any(String.class), anyInt(), anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("append(CharSequence)")
        public void testAppendCharSequence() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                wrapped.append(SOURCE);
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).append(any());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        public void testAppendCharSequenceRange() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 5, SOURCE.length());
                    wrapped.append(SOURCE, index, to);
                    index = to;
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).append(any(), anyInt(), anyInt());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("append(char)")
        public void testAppendChar() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    wrapped.append(SOURCE.charAt(i));
                }
            }
            assertEquals(SOURCE, output.toString());
            verify(output, atLeastOnce()).append(anyChar());
            verify(output, never()).close();
        }

        @Test
        @DisplayName("flush()")
        public void testFlush() throws IOException {
            StringWriter output = spy(new StringWriter(SOURCE.length()));

            try (Writer wrapped = dontClose(output)) {
                wrapped.flush();
            }
            verify(output, times(1)).flush();
            verify(output, never()).close();
        }

        @Test
        @DisplayName("toString()")
        public void testToString() throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            try (Writer wrapped = dontClose(output)) {
                assertThat(wrapped.toString(), not(containsString("StreamUtils$")));
                assertEquals(StreamUtils.class.getName() + "#dontClose(" + output + ")", wrapped.toString());
            }
        }
    }

    @Test
    @DisplayName("checkOffsetAndLength(byte[], int, int)")
    public void testCheckOffsetAndLengthForByteArray() {
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
    public void testCheckOffsetAndLengthForCharArray() {
        char[] array = SOURCE.toCharArray();
        checkOffsetAndLength(array, 0, array.length);
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 0, array.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, array.length));
        checkOffsetAndLength(array, 1, 0);
    }

    @Test
    @DisplayName("checkStartAndEnd(CharSequence, int, int)")
    public void testCheckStartAndEndForCharSequence() {
        checkStartAndEnd(SOURCE, 0, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 0, SOURCE.length() + 1));
        checkStartAndEnd(SOURCE, 1, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, 0));
    }
}
