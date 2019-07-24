/*
 * CharSequenceReaderTest.java
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

import static com.github.robtimus.io.stream.TestData.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

@SuppressWarnings({ "javadoc", "nls" })
public class CharSequenceReaderTest {

    @Test
    @DisplayName("read()")
    public void testReadChar() throws IOException {
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(SOURCE, 1, SOURCE.length() - 1)) {
            int c;
            while ((c = reader.read()) != -1) {
                writer.write(c);
            }
        }
        assertEquals(SOURCE.substring(1, SOURCE.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("read(char[], int, int)")
    public void testReadCharArrayRange() throws IOException {
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(SOURCE, 1, SOURCE.length() - 1)) {
            assertEquals(0, reader.read(new char[5], 0, 0));
            assertEquals("", writer.toString());
            copy(reader, writer, 5);
        }
        assertEquals(SOURCE.substring(1, SOURCE.length() - 1), writer.toString());

        writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(new StringBuilder(SOURCE), 1, SOURCE.length() - 1)) {
            assertEquals(0, reader.read(new char[5], 0, 0));
            assertEquals("", writer.toString());
            copy(reader, writer, 5);
        }
        assertEquals(SOURCE.substring(1, SOURCE.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("skip(long)")
    public void testSkip() throws IOException {
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(SOURCE, 1, SOURCE.length() - 1)) {
            char[] data = new char[10];
            int len = reader.read(data);
            assertEquals(data.length, len);
            writer.write(data);
            assertEquals(0, reader.skip(0));
            assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
            assertEquals(10, reader.skip(10));
            copy(reader, writer);
            assertEquals(0, reader.skip(1));
        }
        assertEquals(SOURCE.substring(1, 11) + SOURCE.substring(21, SOURCE.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("ready()")
    public void testReady() throws IOException {
        try (Reader reader = new CharSequenceReader(SOURCE, 1, SOURCE.length() - 1)) {
            for (int i = 1; i < SOURCE.length() - 1; i++) {
                assertTrue(reader.ready());
                assertNotEquals(-1, reader.read());
            }
            assertFalse(reader.ready());
            assertEquals(-1, reader.read());
        }
    }

    @Test
    @DisplayName("mark(int) and reset()")
    public void testMarkReset() throws IOException {
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(SOURCE, 1, SOURCE.length() - 1)) {
            assertTrue(reader.markSupported());
            reader.mark(5);
            copy(reader, writer);
            reader.reset();
            copy(reader, writer);
        }
        String singleExpected = SOURCE.substring(1, SOURCE.length() - 1);
        assertEquals(singleExpected + singleExpected, writer.toString());
    }

    @Test
    @DisplayName("operations on closed stream")
    public void testOperationsOnClosedStream() throws IOException {
        @SuppressWarnings("resource")
        CharSequenceReader reader = new CharSequenceReader(SOURCE);
        reader.close();
        assertClosed(() -> reader.read());
        assertClosed(() -> reader.read(new char[5], 0, 0));
        assertClosed(() -> reader.skip(5));
        assertClosed(() -> reader.ready());
        assertClosed(() -> reader.mark(5));
        assertClosed(() -> reader.reset());
        reader.close();
    }

    private void assertClosed(Executable executable) {
        IOException thrown = assertThrows(IOException.class, executable);
        assertEquals(Messages.stream.closed.get(), thrown.getMessage());
    }

    private void copy(Reader reader, Writer writer) throws IOException {
        copy(reader, writer, 4096);
    }

    private void copy(Reader reader, Writer writer, int bufferSize) throws IOException {
        char[] buffer = new char[bufferSize];
        int len;
        while ((len = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, len);
        }
    }
}
