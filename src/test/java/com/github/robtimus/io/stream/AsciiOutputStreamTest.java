/*
 * AsciiOutputStreamTest.java
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

import static com.github.robtimus.io.stream.StreamUtils.ascii;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AsciiOutputStreamTest extends TestBase {

    @Test
    @DisplayName("write(int)")
    public void testWriteInt() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        StringWriter output = new StringWriter(SOURCE.length());

        try (OutputStream wrapped = ascii(output)) {
            for (byte b : bytes) {
                wrapped.write(b);
            }

            // with invalid ASCII
            IOException thrown = assertThrows(IOException.class, () -> wrapped.write(-1));
            assertEquals(Messages.ascii.invalidByte.get(-1), thrown.getMessage());
        }
        assertEquals(SOURCE, output.toString());
    }

    @Test
    @DisplayName("write(byte[], int, int)")
    public void testWriteByteArrayRange() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        StringWriter output = new StringWriter(SOURCE.length());

        try (OutputStream wrapped = ascii(output)) {
            int index = 0;
            while (index < bytes.length) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.write(bytes, index, to - index);
                index = to;
            }

            // with invalid ASCII
            IOException thrown = assertThrows(IOException.class, () -> wrapped.write(new byte[] { 'h', 'e', 'l', 'l', 'o', ' ', -1, }));
            assertEquals(Messages.ascii.invalidByte.get(-1), thrown.getMessage());
        }
        assertEquals(SOURCE, output.toString());

        // write a huge array
        output.getBuffer().delete(0, SOURCE.length());
        bytes = LONG_SOURCE.getBytes();
        try (OutputStream wrapped = ascii(output)) {
            wrapped.write(bytes, 0, bytes.length);
        }
        assertEquals(LONG_SOURCE, output.toString());
    }

    @Test
    @DisplayName("flush()")
    public void testFlush() throws IOException {
        StringWriter output = spy(new StringWriter());
        try (OutputStream wrapped = ascii(output)) {
            wrapped.flush();
        }
        verify(output).flush();
        verify(output).close();
        verifyNoMoreInteractions(output);
    }

    @Test
    @DisplayName("close()")
    public void testClose() throws IOException {
        StringWriter output = spy(new StringWriter());
        try (OutputStream wrapped = ascii(output)) {
            // don't do anything
        }
        verify(output).close();
        verifyNoMoreInteractions(output);
    }

    @Test
    @DisplayName("base64")
    public void testBase64() throws IOException {
        byte[] expected = SOURCE.getBytes();
        String base64 = Base64.getEncoder().encodeToString(expected);
        ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

        try (InputStream decoded = Base64.getDecoder().wrap(ascii(new StringReader(base64)))) {
            copy(decoded, output);
        }
        assertArrayEquals(expected, output.toByteArray());
    }
}