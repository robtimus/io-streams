/*
 * CharacterPipeTest.java
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.CharacterPipe.ArrayBuffer;
import com.github.robtimus.io.stream.CharacterPipe.Buffer;
import com.github.robtimus.io.stream.CharacterPipe.DequeBuffer;
import com.github.robtimus.io.stream.CharacterPipe.SingleValueBuffer;

@SuppressWarnings({ "javadoc", "nls" })
public class CharacterPipeTest extends TestBase {

    private static ScheduledExecutorService executor;

    @BeforeAll
    public static void setupExecutor() {
        executor = Executors.newScheduledThreadPool(2);
    }

    @AfterAll
    public static void destroyExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("CharacterPipe(int)")
    public void testConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterPipe(-1));
        assertThrows(IllegalArgumentException.class, () -> new CharacterPipe(0));
        testConstructor(1, SingleValueBuffer.class);
        testConstructor(2, ArrayBuffer.class);
        testConstructor(1024, ArrayBuffer.class);
        testConstructor(1025, DequeBuffer.class);
    }

    private void testConstructor(int capacity, Class<? extends Buffer> expectedType) {
        CharacterPipe pipe = new CharacterPipe(capacity);
        assertThat(pipe.buffer, instanceOf(expectedType));
    }

    @Test
    @DisplayName("closed()")
    public void testClosed() throws IOException {
        CharacterPipe pipe = new CharacterPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        assertTrue(pipe.closed());

        pipe = new CharacterPipe();
        assertFalse(pipe.closed());
        pipe.output().close();
        assertTrue(pipe.closed());

        pipe = new CharacterPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        pipe.output().close();
        assertTrue(pipe.closed());
    }

    @Test
    @DisplayName("interrupt")
    public void testInterrupt() throws InterruptedException {
        CharacterPipe pipe = new CharacterPipe();
        AtomicReference<InterruptedIOException> exception = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            exception.set(assertThrows(InterruptedIOException.class, () -> pipe.input().read()));
        });
        thread.start();
        thread.interrupt();
        thread.join();

        assertNotNull(exception.get());
        assertThat(exception.get().getCause(), instanceOf(InterruptedException.class));
    }

    @Nested
    @DisplayName("input()")
    public class Input {

        private Void writeDataByteByByte(CharacterPipe pipe, String data) throws InterruptedException, IOException {
            try (Writer output = pipe.output()) {
                Thread.sleep(100);
                for (int i = 0; i < data.length(); i++) {
                    output.write(data.charAt(i));
                }
            }
            return null;
        }

        private Void writeDataInChunks(CharacterPipe pipe, String data, int chunkSize) throws InterruptedException, IOException {
            try (Writer output = pipe.output()) {
                Thread.sleep(100);
                int index = 0;
                int remaining = data.length();
                while (remaining > 0) {
                    int count = Math.min(remaining, chunkSize);
                    output.write(data, index, count);
                    index += count;
                    remaining -= count;
                }
            }
            return null;
        }

        @Test
        @DisplayName("read()")
        public void testReadChar() throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            CharacterPipe pipe = new CharacterPipe(1);
            executor.submit(() -> writeDataByteByByte(pipe, SOURCE));
            try (Reader input = pipe.input()) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write(c);
                }
            }
            assertEquals(SOURCE, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        public void testReadCharArrayRange() throws IOException {
            testReadCharArrayRange(3, 5);
            testReadCharArrayRange(5, 3);
        }

        private void testReadCharArrayRange(int capacity, int chunkSize) throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            CharacterPipe pipe = new CharacterPipe(capacity);
            executor.submit(() -> writeDataInChunks(pipe, SOURCE, capacity));
            try (Reader input = pipe.input()) {
                assertEquals(0, input.read(new char[5], 0, 0));
                copy(input, output, chunkSize);
            }
            assertEquals(SOURCE, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        public void testSkip() throws IOException, InterruptedException {
            String expected = SOURCE.substring(0, 5) + SOURCE.substring(11, SOURCE.length());
            StringWriter output = new StringWriter(expected.length());

            CharacterPipe pipe = new CharacterPipe(5);
            executor.submit(() -> writeDataInChunks(pipe, SOURCE, 5));
            try (Reader input = pipe.input()) {
                char[] data = new char[10];
                int len = input.read(data);
                assertEquals(5, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertThrows(IllegalArgumentException.class, () -> input.skip(-1));
                assertEquals(5, input.skip(10));
                // add a small delay to allow the writer to write something again
                Thread.sleep(100);
                assertEquals(1, input.skip(1));
                copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        public void testReady() throws IOException, InterruptedException {
            CharacterPipe pipe = new CharacterPipe(5);
            executor.submit(() -> writeDataInChunks(pipe, SOURCE, 5));
            try (Reader input = pipe.input()) {
                // add a small delay to allow the writer to write something
                Thread.sleep(200);
                for (int i = 5; i > 0; i--) {
                    assertTrue(input.ready());
                    input.read();
                }
                while (input.read() != -1) {
                    // do nothing
                }
                assertFalse(input.ready());
            }
        }
    }

    @Nested
    @DisplayName("output()")
    public class Output {

        private String readAll(CharacterPipe pipe) throws InterruptedException, IOException {
            StringWriter output = new StringWriter();

            Thread.sleep(100);
            try (Reader input = pipe.input()) {
                int c;
                while ((c = input.read()) != -1) {
                    output.write(c);
                }
            }
            return output.toString();
        }

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException, InterruptedException, ExecutionException {
            CharacterPipe pipe = new CharacterPipe(1);
            Future<String> written = executor.submit(() -> readAll(pipe));
            try (Writer output = pipe.output()) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    output.write(SOURCE.charAt(i));
                }
            }
            assertEquals(SOURCE, written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().write('*'));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("write(char[], int, int)")
        public void testWriteCharArrayRange() throws IOException, InterruptedException, ExecutionException {
            testWriteCharArrayRange(5, 5);
            testWriteCharArrayRange(3, 5);
            testWriteCharArrayRange(5, 3);
        }

        private void testWriteCharArrayRange(int capacity, int chunkSize) throws IOException, InterruptedException, ExecutionException {
            CharacterPipe pipe = new CharacterPipe(capacity);
            Future<String> written = executor.submit(() -> readAll(pipe));
            try (Writer output = pipe.output()) {
                int index = 0;
                char[] content = SOURCE.toCharArray();
                while (index < content.length) {
                    int to = Math.min(index + chunkSize, content.length);
                    output.write(content, index, to - index);
                    index = to;
                }
            }
            assertEquals(SOURCE, written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().write(SOURCE.toCharArray()));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("write(String, int, int)")
        public void testWriteStringRange() throws IOException, InterruptedException, ExecutionException {
            testWriteStringRange(5, 5);
            testWriteStringRange(3, 5);
            testWriteStringRange(5, 3);
        }

        private void testWriteStringRange(int capacity, int chunkSize) throws IOException, InterruptedException, ExecutionException {
            CharacterPipe pipe = new CharacterPipe(capacity);
            Future<String> written = executor.submit(() -> readAll(pipe));
            try (Writer output = pipe.output()) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.write(SOURCE, index, to - index);
                    index = to;
                }
            }
            assertEquals(SOURCE, written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().write(SOURCE));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("append(CharSequence)")
        public void testAppendCharSequence() throws IOException, InterruptedException, ExecutionException {
            testAppendCharSequence(5);
            testAppendCharSequence(3);
        }

        private void testAppendCharSequence(int capacity) throws IOException, InterruptedException, ExecutionException {
            CharacterPipe pipe = new CharacterPipe(capacity);
            Future<String> written = executor.submit(() -> readAll(pipe));
            try (Writer output = pipe.output()) {
                output.append(SOURCE);
                output.append(null);
            }
            assertEquals(SOURCE + "null", written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().append(SOURCE));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        public void testAppendCharSequenceRange() throws IOException, InterruptedException, ExecutionException {
            testAppendCharSequenceRange(5, 5);
            testAppendCharSequenceRange(3, 5);
            testAppendCharSequenceRange(5, 3);
        }

        private void testAppendCharSequenceRange(int capacity, int chunkSize) throws IOException, InterruptedException, ExecutionException {
            CharacterPipe pipe = new CharacterPipe(capacity);
            Future<String> written = executor.submit(() -> readAll(pipe));
            try (Writer output = pipe.output()) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.append(SOURCE, index, to);
                    index = to;
                }
                output.append(null, 0, 3);
            }
            assertEquals(SOURCE + "nul", written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().append(SOURCE, 0, SOURCE.length()));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("flush")
        public void testFlush() throws IOException {
            CharacterPipe pipe = new CharacterPipe(5);
            try (Writer output = pipe.output()) {
                output.write('x');
                output.flush();
            }
        }
    }

    @Nested
    @DisplayName("SingleValueBuffer")
    public class SingleValueBufferTest {

        @Test
        @DisplayName("size()")
        public void testSize() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertEquals(0, buffer.size());
            buffer.add('x');
            assertEquals(1, buffer.size());
            buffer.next();
            assertEquals(0, buffer.size());
        }

        @Test
        @DisplayName("isEmpty()")
        public void testIsEmpty() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertTrue(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertTrue(buffer.isEmpty());
        }

        @Test
        @DisplayName("next()")
        public void testNext() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertThrows(IllegalStateException.class, () -> buffer.next());
            char value = 'x';
            buffer.add(value);
            assertEquals(value, buffer.next());
            assertThrows(IllegalStateException.class, () -> buffer.next());
        }

        @Test
        @DisplayName("add(byte)")
        public void testAdd() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            char value = 'x';
            buffer.add(value);
            assertEquals(value, buffer.next());
            buffer.add(value);
            assertThrows(IllegalStateException.class, () -> buffer.add(value));
        }

        @Test
        @DisplayName("clear()")
        public void testClear() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertTrue(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
        }
    }

    @Nested
    @DisplayName("ArrayBuffer")
    public class ArrayBufferTest {

        @Test
        @DisplayName("size()")
        public void testSize() {
            ArrayBuffer buffer = new ArrayBuffer(2);
            assertEquals(0, buffer.size());
            buffer.add('x');
            assertEquals(1, buffer.size());
            buffer.add('x');
            assertEquals(2, buffer.size());
            buffer.next();
            assertEquals(1, buffer.size());
            buffer.next();
            assertEquals(0, buffer.size());
        }

        @Test
        @DisplayName("isEmpty()")
        public void testIsEmpty() {
            ArrayBuffer buffer = new ArrayBuffer(2);
            assertTrue(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertTrue(buffer.isEmpty());
        }

        @Test
        @DisplayName("next()")
        public void testNext() {
            ArrayBuffer buffer = new ArrayBuffer(2);
            assertThrows(IllegalStateException.class, () -> buffer.next());
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            assertThrows(IllegalStateException.class, () -> buffer.next());
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            assertThrows(IllegalStateException.class, () -> buffer.next());
        }

        @Test
        @DisplayName("add(byte)")
        public void testAdd() {
            ArrayBuffer buffer = new ArrayBuffer(2);
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            buffer.add(value1);
            buffer.add(value2);
            assertThrows(IllegalStateException.class, () -> buffer.add(value1));
        }

        @Test
        @DisplayName("clear()")
        public void testClear() {
            ArrayBuffer buffer = new ArrayBuffer(2);
            assertTrue(buffer.isEmpty());
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            assertFalse(buffer.isEmpty());
            buffer.add(value2);
            assertFalse(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
        }
    }

    @Nested
    @DisplayName("DequeBuffer")
    public class DequeBufferTest {

        @Test
        @DisplayName("size()")
        public void testSize() {
            DequeBuffer buffer = new DequeBuffer();
            assertEquals(0, buffer.size());
            buffer.add('x');
            assertEquals(1, buffer.size());
            buffer.add('x');
            assertEquals(2, buffer.size());
            buffer.next();
            assertEquals(1, buffer.size());
            buffer.next();
            assertEquals(0, buffer.size());
        }

        @Test
        @DisplayName("isEmpty()")
        public void testIsEmpty() {
            DequeBuffer buffer = new DequeBuffer();
            assertTrue(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.add('x');
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertTrue(buffer.isEmpty());
        }

        @Test
        @DisplayName("next()")
        public void testNext() {
            DequeBuffer buffer = new DequeBuffer();
            assertThrows(NoSuchElementException.class, () -> buffer.next());
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            assertThrows(NoSuchElementException.class, () -> buffer.next());
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            assertThrows(NoSuchElementException.class, () -> buffer.next());
        }

        @Test
        @DisplayName("add(byte)")
        public void testAdd() {
            DequeBuffer buffer = new DequeBuffer();
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            buffer.add(value2);
            assertEquals(value1, buffer.next());
            assertEquals(value2, buffer.next());
            buffer.add(value1);
            buffer.add(value2);
            // no max size constraints in the buffer itself
            buffer.add(value1);
        }

        @Test
        @DisplayName("clear()")
        public void testClear() {
            DequeBuffer buffer = new DequeBuffer();
            assertTrue(buffer.isEmpty());
            char value1 = 'x';
            char value2 = 'y';
            buffer.add(value1);
            assertFalse(buffer.isEmpty());
            buffer.add(value2);
            assertFalse(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
            buffer.clear();
            assertTrue(buffer.isEmpty());
        }
    }
}
