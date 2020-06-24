/*
 * DontCloseInputStreamTest.java
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

import static com.github.robtimus.io.stream.StreamUtils.dontClose;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DontCloseInputStreamTest extends TestBase {

    @Test
    @DisplayName("read()")
    void testReadByte() throws IOException {
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
    void testReadByteArray() throws IOException {
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
    void testReadByteArrayRange() throws IOException {
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
    void testSkip() throws IOException {
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
    void testAvailable() throws IOException {
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
    void testMarkAndReset() throws IOException {
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
    @DisplayName("operations on closed stream")
    void testOperationsOnClosedStream() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        ByteArrayInputStream input = spy(new ByteArrayInputStream(bytes));

        @SuppressWarnings("resource")
        InputStream wrapped = dontClose(input);
        wrapped.close();
        assertClosed(() -> wrapped.read());
        assertClosed(() -> wrapped.read(new byte[0]));
        assertClosed(() -> wrapped.read(new byte[0], 0, 0));
        assertClosed(() -> wrapped.skip(0));
        assertClosed(() -> wrapped.available());
        assertFalse(wrapped.markSupported());
        wrapped.mark(5);
        assertClosed(() -> wrapped.reset());
        wrapped.close();

        verify(input, never()).markSupported();
        verify(input, never()).mark(anyInt());
    }
}
