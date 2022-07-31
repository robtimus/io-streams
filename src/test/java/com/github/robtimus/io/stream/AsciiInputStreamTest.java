/*
 * AsciiInputStreamTest.java
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class AsciiInputStreamTest extends TestBase {

    @Test
    @DisplayName("read()")
    void testReadByte() throws IOException {
        byte[] expected = SOURCE.getBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

        try (InputStream wrapped = new AsciiInputStream(new StringReader(SOURCE))) {
            int b;
            while ((b = wrapped.read()) != -1) {
                output.write(b);
            }
        }
        assertArrayEquals(expected, output.toByteArray());

        // with invalid ASCII
        String invalid = "é";
        try (InputStream wrapped = new AsciiInputStream(new StringReader(invalid))) {
            IOException thrown = assertThrows(IOException.class, () -> wrapped.read());
            assertEquals(Messages.ascii.invalidChar.get(invalid.charAt(0)), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("read(byte[], int, int)")
    void testReadByteArrayRange() throws IOException {
        byte[] expected = SOURCE.getBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

        try (InputStream wrapped = new AsciiInputStream(new StringReader(SOURCE))) {
            byte[] buffer = new byte[1024];
            final int offset = 100;
            int len;
            while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                output.write(buffer, 100, len);
            }
        }
        assertArrayEquals(expected, output.toByteArray());

        // read a huge array
        output.reset();
        expected = LONG_SOURCE.getBytes();

        try (InputStream wrapped = new AsciiInputStream(new StringReader(LONG_SOURCE))) {
            byte[] buffer = new byte[2048];
            final int offset = 100;
            int len;
            while ((len = wrapped.read(buffer, offset, buffer.length - offset)) != -1) {
                output.write(buffer, 100, len);
            }
        }
        assertArrayEquals(expected, output.toByteArray());

        // with invalid ASCII
        String invalid = "hello é";
        try (InputStream wrapped = new AsciiInputStream(new StringReader(invalid))) {
            IOException thrown = assertThrows(IOException.class, () -> wrapped.read(new byte[1024]));
            assertEquals(Messages.ascii.invalidChar.get(invalid.charAt(invalid.length() - 1)), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("available()")
    void testAvailable() throws IOException {
        try (Reader reader = new CharSequenceReader(SOURCE);
                InputStream wrapped = new AsciiInputStream(reader)) {

            while (wrapped.read() != -1) {
                int expected = reader.ready() ? 1 : 0;
                assertEquals(expected, wrapped.available());
            }
            assertEquals(0, wrapped.available());
        }
    }

    @Test
    @DisplayName("close()")
    void testClose() throws IOException {
        @SuppressWarnings("resource")
        Reader input = mock(Reader.class);
        try (InputStream wrapped = new AsciiInputStream(input)) {
            // don't do anything
        }
        verify(input).close();
        verifyNoMoreInteractions(input);
    }

    @Test
    @DisplayName("base64")
    void testBase64() throws IOException {
        byte[] expected = SOURCE.getBytes();
        String base64 = Base64.getEncoder().encodeToString(expected);
        ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

        try (InputStream wrapped = new AsciiInputStream(new StringReader(base64));
                InputStream decoded = Base64.getDecoder().wrap(wrapped)) {

            IOUtils.copy(decoded, output);
        }
        assertArrayEquals(expected, output.toByteArray());
    }
}
