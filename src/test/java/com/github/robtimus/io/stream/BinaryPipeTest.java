/*
 * BinaryPipeTest.java
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class BinaryPipeTest extends TestBase {

    @Test
    @DisplayName("closed()")
    public void testClosed() {
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

    // these two classes use regular threads and not an executor, so the thread will have ended once the action has ended

    @Nested
    @DisplayName("input()")
    public class Input {

        @Test
        @DisplayName("pipe()")
        public void testPipe() {
            BinaryPipe pipe = new BinaryPipe();
            @SuppressWarnings("resource")
            PipeInputStream input = pipe.input();
            assertSame(pipe, input.pipe());
        }

        @Test
        @DisplayName("read()")
        public void testReadByte() throws IOException {
            byte[] expected = SOURCE.getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe();
            new Thread(() -> writeDataByteByByte(pipe, expected)).start();
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
            byte[] expected = SOURCE.getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe();
            new Thread(() -> writeDataInChunks(pipe, expected, 10)).start();
            try (InputStream input = pipe.input()) {
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new byte[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new byte[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new byte[5], 1, 5));

                assertEquals(0, input.read(new byte[5], 0, 0));
                copy(input, output, 5);
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("skip(long)")
        public void testSkip() throws IOException {
            byte[] expected = (SOURCE.substring(0, 5) + SOURCE.substring(11, SOURCE.length())).getBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);

            BinaryPipe pipe = new BinaryPipe();
            new Thread(() -> writeDataInChunks(pipe, SOURCE.getBytes(), 5)).start();
            try (InputStream input = pipe.input()) {
                byte[] data = new byte[10];
                int len = input.read(data);
                assertEquals(5, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertEquals(0, input.skip(-1));
                assertEquals(5, input.skip(10));
                assertEquals(1, input.skip(1));
                copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertArrayEquals(expected, output.toByteArray());
        }

        @Test
        @DisplayName("available()")
        public void testAvailable() throws IOException, InterruptedException {
            BinaryPipe pipe = new BinaryPipe();
            new Thread(() -> writeDataInChunks(pipe, SOURCE.getBytes(), 5)).start();
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
                assertEquals(0, input.available());
            }
        }

        @Test
        @DisplayName("operations with write error")
        public void testOperationsWithWriteError() throws IOException {
            BinaryPipe pipe = new BinaryPipe();
            try (InputStream input = pipe.input()) {
                IOException error = new IOException();

                @SuppressWarnings("resource")
                PipeOutputStream output = pipe.output();
                output.flush();
                output.close(error);

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new byte[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.available()));

                output.close();

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new byte[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.available()));

                output.close(null);

                assertEquals(-1, input.read());
                assertEquals(-1, input.read(new byte[5], 0, 5));
                assertEquals(0, input.skip(5));
                assertEquals(0, input.available());
            }
        }

        @Nested
        @DisplayName("writer died")
        public class WriterDied {

            @Test
            @DisplayName("read()")
            public void testReadByte() throws IOException {
                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (InputStream input = pipe.input()) {
                    // perform one read to consume the data
                    input.read();
                    IOException thrown = assertThrows(IOException.class, () -> {
                        input.read();
                    });
                    assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("read(byte[], int, int)")
            public void testReadByteArrayRange() throws IOException {
                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (InputStream input = pipe.input()) {
                    // perform one read to consume the data
                    assertEquals(1, input.read(new byte[5], 0, 5));
                    IOException thrown = assertThrows(IOException.class, () -> {
                        input.read(new byte[5], 0, 5);
                    });
                    assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("skip(long)")
            public void testSkip() throws IOException {
                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (InputStream input = pipe.input()) {
                    // perform one skip to consume the data
                    assertEquals(1, input.skip(10));
                    IOException thrown = assertThrows(IOException.class, () -> {
                        input.skip(1);
                    });
                    assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("output()")
    public class Output {

        @Test
        @DisplayName("pipe()")
        public void testPipe() {
            BinaryPipe pipe = new BinaryPipe();
            @SuppressWarnings("resource")
            PipeOutputStream output = pipe.output();
            assertSame(pipe, output.pipe());
        }

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException, InterruptedException {
            byte[] expected = SOURCE.getBytes();

            BinaryPipe pipe = new BinaryPipe();
            AtomicReference<byte[]> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (OutputStream output = pipe.output()) {
                for (byte b : expected) {
                    output.write(b);
                }
            }
            readLatch.await();
            assertArrayEquals(expected, result.get());

            assertClosed(() -> pipe.output().write(0));
        }

        @Test
        @DisplayName("write(byte[], int, int)")
        public void testWriteByteArrayRange() throws IOException, InterruptedException {
            byte[] expected = SOURCE.getBytes();

            BinaryPipe pipe = new BinaryPipe();
            AtomicReference<byte[]> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (OutputStream output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new byte[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new byte[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new byte[5], 1, 5));

                int index = 0;
                while (index < expected.length) {
                    int to = Math.min(index + 10, expected.length);
                    output.write(expected, index, to - index);
                    index = to;
                }
            }
            readLatch.await();
            assertArrayEquals(expected, result.get());

            assertClosed(() -> pipe.output().write(expected));
        }

        @Test
        @DisplayName("flush()")
        public void testFlush() throws IOException {
            BinaryPipe pipe = new BinaryPipe();
            try (OutputStream output = pipe.output()) {
                output.flush();
            }
        }

        @Test
        @DisplayName("operations with read error")
        public void testOperationsWithReadError() throws IOException {
            byte[] bytes = SOURCE.getBytes();

            BinaryPipe pipe = new BinaryPipe();
            try (OutputStream output = pipe.output()) {
                IOException error = new IOException();
                pipe.input().close(error);

                assertSame(error, assertThrows(IOException.class, () -> output.write(bytes[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.write(bytes, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.flush()));

                pipe.input().close();

                assertSame(error, assertThrows(IOException.class, () -> output.write(bytes[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.write(bytes, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.flush()));

                pipe.input().close(null);

                assertClosed(() -> output.write(bytes[0]));
                assertClosed(() -> output.write(bytes, 0, 5));
                assertClosed(() -> output.flush());
            }
        }

        @Nested
        @DisplayName("parallel writes")
        public class ParallelWrites {

            @Test
            @DisplayName("write(int)")
            public void testWriteInt() throws InterruptedException {
                byte[] bytes = SOURCE.getBytes();

                BinaryPipe pipe = new BinaryPipe();
                AtomicReference<byte[]> result = new AtomicReference<>(null);
                CountDownLatch readLatch = new CountDownLatch(1);
                new Thread(() -> readAll(pipe, result, readLatch)).start();
                int threadCount = 3;
                CountDownLatch writeLatch = new CountDownLatch(threadCount);
                CountDownLatch closeLatch = new CountDownLatch(1);
                for (int i = 0; i < threadCount; i++) {
                    new Thread(() -> writeDataByteByByte(pipe.output(), bytes, writeLatch, closeLatch)).start();
                }
                writeLatch.await();
                pipe.output().close();
                closeLatch.countDown();
                readLatch.await();

                byte[] expected = new byte[bytes.length * 3];
                System.arraycopy(bytes, 0, expected, 0, bytes.length);
                System.arraycopy(bytes, 0, expected, bytes.length, bytes.length);
                System.arraycopy(bytes, 0, expected, 2 * bytes.length, bytes.length);
                byte[] actual = result.get();
                Arrays.sort(expected);
                Arrays.sort(actual);
                assertArrayEquals(expected, actual);

                assertClosed(() -> pipe.output().write(0));
            }

            @Test
            @DisplayName("write(byte[], int, int)")
            public void testWriteByteArrayRange() throws InterruptedException {
                byte[] bytes = SOURCE.getBytes();

                BinaryPipe pipe = new BinaryPipe();
                AtomicReference<byte[]> result = new AtomicReference<>(null);
                CountDownLatch readLatch = new CountDownLatch(1);
                new Thread(() -> readAll(pipe, result, readLatch)).start();
                int threadCount = 3;
                CountDownLatch writeLatch = new CountDownLatch(threadCount);
                CountDownLatch closeLatch = new CountDownLatch(1);
                for (int i = 0; i < threadCount; i++) {
                    new Thread(() -> writeDataInChunks(pipe.output(), bytes, 10, writeLatch, closeLatch)).start();
                }
                writeLatch.await();
                pipe.output().close();
                closeLatch.countDown();
                readLatch.await();

                byte[] expected = new byte[bytes.length * 3];
                System.arraycopy(bytes, 0, expected, 0, bytes.length);
                System.arraycopy(bytes, 0, expected, bytes.length, bytes.length);
                System.arraycopy(bytes, 0, expected, 2 * bytes.length, bytes.length);
                byte[] actual = result.get();
                Arrays.sort(expected);
                Arrays.sort(actual);
                assertArrayEquals(expected, actual);

                assertClosed(() -> pipe.output().write(bytes));
            }
        }

        @Nested
        @DisplayName("reader died")
        public class ReaderDied {

            @Test
            @DisplayName("write(int)")
            public void testWriteInt() throws IOException {
                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (OutputStream output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(0);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("write(byte[], int, int)")
            public void testWriteByteArrayRange() throws IOException {
                byte[] bytes = SOURCE.getBytes();

                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (OutputStream output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(bytes, 0, bytes.length);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("flush()")
            public void testFlush() throws IOException {
                BinaryPipe pipe = new BinaryPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (OutputStream output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.flush();
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }
        }
    }

    private void writeDataByteByByte(BinaryPipe pipe, byte[] data) {
        try (PipeOutputStream output = pipe.output()) {
            for (byte b : data) {
                output.write(b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDataByteByByte(PipeOutputStream output, byte[] data, CountDownLatch writeLatch, CountDownLatch closeLatch) {
        try {
            for (byte b : data) {
                output.write(b);
            }
            writeLatch.countDown();
            closeLatch.await();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void writeDataInChunks(BinaryPipe pipe, byte[] data, int chunkSize) {
        try (PipeOutputStream output = pipe.output()) {
            int index = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int count = Math.min(remaining, chunkSize);
                output.write(data, index, count);
                index += count;
                remaining -= count;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDataInChunks(PipeOutputStream output, byte[] data, int chunkSize, CountDownLatch writeLatch, CountDownLatch closeLatch) {
        try {
            int index = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int count = Math.min(remaining, chunkSize);
                output.write(data, index, count);
                index += count;
                remaining -= count;
            }
            writeLatch.countDown();
            closeLatch.await();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void writeAndDie(PipeOutputStream output) {
        try {
            output.write(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readAll(BinaryPipe pipe, AtomicReference<byte[]> result, CountDownLatch readLatch) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream input = pipe.input()) {
            int b;
            while ((b = input.read()) != -1) {
                output.write(b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.set(output.toByteArray());
        readLatch.countDown();
    }

    private void skipAndDie(PipeInputStream input) {
        try {
            input.skip(Integer.MAX_VALUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
