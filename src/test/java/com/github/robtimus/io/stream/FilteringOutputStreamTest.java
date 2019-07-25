/*
 * FilteringOutputStreamTest.java
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class FilteringOutputStreamTest extends TestBase {

    @Test
    @DisplayName("write(int)")
    public void testWriteInt() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

        try (OutputStream wrapped = new FilteringOutputStream(output, Character::isWhitespace)) {
            for (byte b : bytes) {
                wrapped.write(b);
            }
        }
        assertArrayEquals(expected, output.toByteArray());
    }

    @Test
    @DisplayName("write(byte[], int, int)")
    public void testWriteByteArrayRange() throws IOException {
        byte[] bytes = SOURCE.getBytes();
        byte[] expected = SOURCE.replaceAll("\\s+", "").getBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);

        try (OutputStream wrapped = new FilteringOutputStream(output, Character::isWhitespace)) {
            int index = 0;
            while (index < bytes.length) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.write(bytes, index, to - index);
                index = to;
            }
        }
        assertArrayEquals(expected, output.toByteArray());

        // write a huge array
        output.reset();
        bytes = LONG_SOURCE.getBytes();
        expected = LONG_SOURCE.replaceAll("\\s+", "").getBytes();
        try (OutputStream wrapped = new FilteringOutputStream(output, Character::isWhitespace)) {
            wrapped.write(bytes, 0, bytes.length);
        }
        assertArrayEquals(expected, output.toByteArray());
    }

    @Test
    @DisplayName("flush()")
    public void testFlush() throws IOException {
        ByteArrayOutputStream output = spy(new ByteArrayOutputStream());

        try (OutputStream wrapped = new FilteringOutputStream(output, Character::isWhitespace)) {
            wrapped.flush();
        }
        verify(output, times(1)).flush();
    }
}
