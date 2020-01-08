/*
 * FilteringWriterTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class FilteringWriterTest extends TestBase {

    @Test
    @DisplayName("write(int)")
    public void testWriteInt() throws IOException {
        String expected = SOURCE.replaceAll("\\s+", "");
        StringWriter output = new StringWriter(SOURCE.length());

        try (Writer wrapped = new FilteringWriter(output, Character::isWhitespace)) {
            for (int i = 0; i < SOURCE.length(); i++) {
                wrapped.write(SOURCE.charAt(i));
            }
        }
        assertEquals(expected, output.toString());
    }

    @Test
    @DisplayName("write(char[], int, int)")
    public void testWriteCharArrayRange() throws IOException {
        String expected = SOURCE.replaceAll("\\s+", "");
        StringWriter output = new StringWriter(SOURCE.length());

        try (Writer wrapped = new FilteringWriter(output, Character::isWhitespace)) {
            char[] chars = SOURCE.toCharArray();
            int index = 0;
            while (index < chars.length) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.write(chars, index, to - index);
                index = to;
            }
        }
        assertEquals(expected, output.toString());

        // write a huge array
        expected = LONG_SOURCE.replaceAll("\\s+", "");
        output = new StringWriter(LONG_SOURCE.length());
        try (Writer wrapped = new FilteringWriter(output, Character::isWhitespace)) {
            wrapped.write(LONG_SOURCE.toCharArray(), 0, LONG_SOURCE.length());
        }
        assertEquals(expected, output.toString());
    }

    @Test
    @DisplayName("flush()")
    public void testFlush() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new FilteringWriter(output, Character::isWhitespace)) {
            wrapped.flush();
        }
        verify(output).flush();
        verify(output).close();
        verifyNoMoreInteractions(output);
    }

    @Test
    @DisplayName("close()")
    public void testClose() throws IOException {
        StringWriter output = spy(new StringWriter(0));
        try (Writer wrapped = new FilteringWriter(output, Character::isWhitespace)) {
            // don't do anything
        }
        verify(output).close();
        verifyNoMoreInteractions(output);
    }
}
