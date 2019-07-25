/*
 * FilteringInputStreamTest.java
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

import static com.github.robtimus.io.stream.StreamUtils.filter;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class FilteringInputStreamTest extends TestBase {

    @Test
    @DisplayName("read()")
    public void testReadByte() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

        try (InputStream wrapped = filter(input, Character::isWhitespace)) {
            int b;
            while ((b = wrapped.read()) != -1) {
                output.write(b);
            }
        }
        assertArrayEquals(expected, output.toByteArray());
    }

    @Test
    @DisplayName("read(byte[], int, int)")
    public void testReadByteArrayRange() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

        try (InputStream wrapped = filter(input, Character::isWhitespace)) {
            byte[] buffer = new byte[1024];
            final int offset = 100;
            int len;
            while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                output.write(buffer, 100, len);
            }
        }
        assertArrayEquals(expected, output.toByteArray());
    }

    @Test
    @DisplayName("skip(long)")
    public void testSkip() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);

        try (InputStream wrapped = filter(input, Character::isWhitespace)) {
            assertEquals(expected.length, wrapped.skip(Integer.MAX_VALUE));
            assertEquals(-1, wrapped.read());
        }
    }

    @Test
    @DisplayName("available()")
    public void testAvailable() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);

        try (InputStream wrapped = filter(input, Character::isWhitespace)) {
            assertEquals(0, wrapped.available());
        }
    }

    @Test
    @DisplayName("mark(int) and reset")
    public void testMarkAndReset() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

        try (InputStream wrapped = filter(input, Character::isWhitespace)) {
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
        assertArrayEquals(expected, output.toByteArray());
    }
}
