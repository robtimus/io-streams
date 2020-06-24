/*
 * DontCloseOutputStreamTest.java
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DontCloseOutputStreamTest extends TestBase {

    @Test
    @DisplayName("write(int)")
    void testWriteInt() throws IOException {
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
    void testWriteByteArray() throws IOException {
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
    void testWriteByteArrayRange() throws IOException {
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
    void testFlush() throws IOException {
        ByteArrayOutputStream output = spy(new ByteArrayOutputStream());

        try (OutputStream wrapped = dontClose(output)) {
            wrapped.flush();
        }
        verify(output, times(1)).flush();
        verify(output, never()).close();
    }

    @Test
    @DisplayName("operations on closed stream")
    void testOperationsOnClosedStream() throws IOException {
        ByteArrayOutputStream output = spy(new ByteArrayOutputStream());

        @SuppressWarnings("resource")
        OutputStream wrapped = dontClose(output);
        wrapped.close();
        assertClosed(() -> wrapped.write(0));
        assertClosed(() -> wrapped.write(new byte[0]));
        assertClosed(() -> wrapped.write(new byte[0], 0, 0));
        assertClosed(() -> wrapped.flush());
        wrapped.close();
    }
}
