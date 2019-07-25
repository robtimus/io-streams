/*
 * FilteringReaderTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class FilteringReaderTest extends TestBase {

    @Test
    @DisplayName("read()")
    public void testReadChar() throws IOException {
        String expected = SOURCE.replaceAll("\\s+", "");
        StringReader input = new StringReader(SOURCE);
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new FilteringReader(input, Character::isWhitespace)) {
            int c;
            while ((c = wrapped.read()) != -1) {
                output.append((char) c);
            }
        }
        assertEquals(expected, output.toString());
    }

    @Test
    @DisplayName("read(char[], int, int)")
    public void testReadByteArrayRange() throws IOException {
        String expected = SOURCE.replaceAll("\\s+", "");
        StringReader input = new StringReader(SOURCE);
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new FilteringReader(input, Character::isWhitespace)) {
            char[] buffer = new char[1024];
            final int offset = 100;
            int len;
            while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                output.append(buffer, 100, len);
            }
        }
        assertEquals(expected, output.toString());
    }

    @Test
    @DisplayName("ready()")
    public void testReady() throws IOException {
        StringReader input = new StringReader(SOURCE);

        try (Reader wrapped = new FilteringReader(input, Character::isWhitespace)) {
            assertFalse(wrapped.ready());
        }
    }

    @Test
    @DisplayName("mark(int) and reset")
    public void testMarkAndReset() throws IOException {
        String expected = SOURCE.replaceAll("\\s+", "");
        StringReader input = new StringReader(SOURCE);
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new FilteringReader(input, Character::isWhitespace)) {
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
        assertEquals(expected, output.toString());
    }
}
