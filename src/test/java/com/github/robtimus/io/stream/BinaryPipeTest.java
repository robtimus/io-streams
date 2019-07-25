/*
 * BinaryPipeTest.java
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
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
import com.github.robtimus.io.stream.BinaryPipe.ArrayBuffer;
import com.github.robtimus.io.stream.BinaryPipe.Buffer;
import com.github.robtimus.io.stream.BinaryPipe.DequeBuffer;
import com.github.robtimus.io.stream.BinaryPipe.SingleValueBuffer;

@SuppressWarnings("javadoc")
public class BinaryPipeTest extends TestBase {

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
    @DisplayName("BinaryPipe(int)")
    public void testConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new BinaryPipe(-1));
        assertThrows(IllegalArgumentException.class, () -> new BinaryPipe(0));
        testConstructor(1, SingleValueBuffer.class);
        testConstructor(2, ArrayBuffer.class);
        testConstructor(1024, ArrayBuffer.class);
        testConstructor(1025, DequeBuffer.class);
    }

    private void testConstructor(int capacity, Class<? extends Buffer> expectedType) {
        BinaryPipe pipe = new BinaryPipe(capacity);
        assertThat(pipe.buffer, instanceOf(expectedType));
    }

    @Test
    @DisplayName("closed()")
    public void testClosed() throws IOException {
        BinaryPipe pipe = new BinaryPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        assertTrue(pipe.closed());

        pipe = new BinaryPipe();
        assertFalse(pipe.closed());
        pipe.output().close();
        assertTrue(pipe.closed());

        pipe = new BinaryPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        pipe.output().close();
        assertTrue(pipe.closed());
    }

    @Test
    @DisplayName("interrupt")
    public void testInterrupt() throws InterruptedException {
        BinaryPipe pipe = new BinaryPipe();
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

        private Void writeDataByteByByte(BinaryPipe pipe, byte[] data) throws InterruptedException, IOException {
            try (OutputStream output = pipe.output()) {
                Thread.sleep(100);
                for (byte b : data) {
                    output.write(b);
                }
            }
            return null;
        }

        private Void writeDataInChunks(BinaryPipe pipe, byte[] data, int chunkSize) throws InterruptedException, IOException {
            try (OutputStream output = pipe.output()) {
                Thread.sleep(100);
                int index = 0;
                int remaining = data.length;
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
        public void testReadByte() throws IOException {
            byte[] expected = SOURCE.getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe(1);
            executor.submit(() -> writeDataByteByByte(pipe, expected));
            try (InputStream input = pipe.input()) {
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        public void testReadByteArrayRange() throws IOException {
            testReadByteArrayRange(3, 5);
            testReadByteArrayRange(5, 3);
        }

        private void testReadByteArrayRange(int capacity, int chunkSize) throws IOException {
            byte[] expected = SOURCE.getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe(capacity);
            executor.submit(() -> writeDataInChunks(pipe, expected, capacity));
            try (InputStream input = pipe.input()) {
                assertEquals(0, input.read(new byte[5], 0, 0));
                copy(input, output, chunkSize);
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        public void testSkip() throws IOException, InterruptedException {
            byte[] expected = (SOURCE.substring(0, 5) + SOURCE.substring(11, SOURCE.length())).getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe(5);
            executor.submit(() -> writeDataInChunks(pipe, SOURCE.getBytes(), 5));
            try (InputStream input = pipe.input()) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(5, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertThrows(IllegalArgumentException.class, () -> input.skip(-1));
                assertEquals(5, input.skip(10));
                // add a small delay to allow the output stream to write something again
                Thread.sleep(100);
                assertEquals(1, input.skip(1));
                copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        public void testAvailable() throws IOException, InterruptedException {
            BinaryPipe pipe = new BinaryPipe(5);
            executor.submit(() -> writeDataInChunks(pipe, SOURCE.getBytes(), 5));
            try (InputStream input = pipe.input()) {
                // add a small delay to allow the output stream to write something
                Thread.sleep(200);
                for (int i = 5; i > 0; i--) {
                    assertEquals(i, input.available());
                    input.read();
                }
                while (input.read() != -1) {
                    // do nothing
                }
            }
        }
    }

    @Nested
    @DisplayName("output()")
    public class Output {

        private byte[] readAll(BinaryPipe pipe) throws InterruptedException, IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            Thread.sleep(100);
            try (InputStream input = pipe.input()) {
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            }
            return output.toByteArray();
        }

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException, InterruptedException, ExecutionException {
            byte[] expected = SOURCE.getBytes();

            BinaryPipe pipe = new BinaryPipe(1);
            Future<byte[]> written = executor.submit(() -> readAll(pipe));
            try (OutputStream output = pipe.output()) {
                for (byte b : expected) {
                    output.write(b);
                }
            }
            assertArrayEquals(expected, written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().write('*'));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        public void testWriteByteArrayRange() throws IOException, InterruptedException, ExecutionException {
            testWriteByteArrayRange(5, 5);
            testWriteByteArrayRange(3, 5);
            testWriteByteArrayRange(5, 3);
        }

        private void testWriteByteArrayRange(int capacity, int chunkSize) throws IOException, InterruptedException, ExecutionException {
            byte[] expected = SOURCE.getBytes();

            BinaryPipe pipe = new BinaryPipe(capacity);
            Future<byte[]> written = executor.submit(() -> readAll(pipe));
            try (OutputStream output = pipe.output()) {
                int index = 0;
                while (index < expected.length) {
                    int to = Math.min(index + chunkSize, expected.length);
                    output.write(expected, index, to - index);
                    index = to;
                }
            }
            assertArrayEquals(expected, written.get());

            IOException exception = assertThrows(IOException.class, () -> pipe.output().write(expected));
            assertEquals(Messages.stream.closed.get(), exception.getMessage());
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
            buffer.add((byte) 0);
            assertEquals(1, buffer.size());
            buffer.next();
            assertEquals(0, buffer.size());
        }

        @Test
        @DisplayName("isEmpty()")
        public void testIsEmpty() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertTrue(buffer.isEmpty());
            buffer.add((byte) 0);
            assertFalse(buffer.isEmpty());
            buffer.next();
            assertTrue(buffer.isEmpty());
        }

        @Test
        @DisplayName("next()")
        public void testNext() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            assertThrows(IllegalStateException.class, () -> buffer.next());
            byte value = 13;
            buffer.add(value);
            assertEquals(value, buffer.next());
            assertThrows(IllegalStateException.class, () -> buffer.next());
        }

        @Test
        @DisplayName("add(byte)")
        public void testAdd() {
            SingleValueBuffer buffer = new SingleValueBuffer();
            byte value = 13;
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
            buffer.add((byte) 0);
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
            buffer.add((byte) 0);
            assertEquals(1, buffer.size());
            buffer.add((byte) 0);
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
            buffer.add((byte) 0);
            assertFalse(buffer.isEmpty());
            buffer.add((byte) 0);
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
            byte value1 = 13;
            byte value2 = 14;
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
            byte value1 = 13;
            byte value2 = 14;
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
            byte value1 = 13;
            byte value2 = 14;
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
            buffer.add((byte) 0);
            assertEquals(1, buffer.size());
            buffer.add((byte) 0);
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
            buffer.add((byte) 0);
            assertFalse(buffer.isEmpty());
            buffer.add((byte) 0);
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
            byte value1 = 13;
            byte value2 = 14;
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
            byte value1 = 13;
            byte value2 = 14;
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
            byte value1 = 13;
            byte value2 = 14;
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
