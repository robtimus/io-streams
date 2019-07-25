/*
 * TeeWriterTest.java
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class TeeWriterTest extends TestBase {

    @Test
    @DisplayName("write(int)")
    public void testWriteInt() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            for (int i = 0; i < SOURCE.length(); i++) {
                writer.write(SOURCE.charAt(i));
            }
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("write(char[])")
    public void testWriteCharArray() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            writer.write(SOURCE.toCharArray());
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("write(char[], int, int)")
    public void testWriteCharArrayRange() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            char[] content = SOURCE.toCharArray();
            int index = 0;
            while (index < SOURCE.length()) {
                int to = Math.min(index + 5, SOURCE.length());
                writer.write(content, index, to - index);
                index = to;
            }
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("write(String)")
    public void testWriteString() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            writer.write(SOURCE);
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("write(String, int, int)")
    public void testWriteStringRange() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            int index = 0;
            while (index < SOURCE.length()) {
                int to = Math.min(index + 5, SOURCE.length());
                writer.write(SOURCE, index, to - index);
                index = to;
            }
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("append(CharSequence)")
    public void testAppendCharSequence() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            writer.append(SOURCE);
            writer.append(null);
        }
        assertEquals(SOURCE + "null", writer1.toString());
        assertEquals(SOURCE + "null", writer2.toString());
    }

    @Test
    @DisplayName("append(CharSequence, int, int)")
    public void testAppendCharSequenceRange() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            int index = 0;
            while (index < SOURCE.length()) {
                int to = Math.min(index + 5, SOURCE.length());
                writer.append(SOURCE, index, to);
                index = to;
            }
            writer.append(null, 0, 2);
            writer.append(null, 2, 4);
        }
        assertEquals(SOURCE + "null", writer1.toString());
        assertEquals(SOURCE + "null", writer2.toString());
    }

    @Test
    @DisplayName("append(char)")
    public void testAppendChar() throws IOException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            for (int i = 0; i < SOURCE.length(); i++) {
                writer.append(SOURCE.charAt(i));
            }
        }
        assertEquals(SOURCE, writer1.toString());
        assertEquals(SOURCE, writer2.toString());
    }

    @Test
    @DisplayName("flush()")
    public void testFlush() throws IOException {
        StringWriter writer1 = spy(new StringWriter());
        StringWriter writer2 = spy(new StringWriter());
        try (Writer writer = new TeeWriter(writer1, writer2)) {
            writer.flush();
        }
        verify(writer1).flush();
        verify(writer2).flush();
        verify(writer1).close();
        verify(writer2).close();
        verifyNoMoreInteractions(writer1, writer2);
    }
}
