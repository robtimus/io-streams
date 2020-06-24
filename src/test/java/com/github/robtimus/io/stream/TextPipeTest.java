/*
 * TextPipeTest.java
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class TextPipeTest extends TestBase {

    @Test
    @DisplayName("closed()")
    void testClosed() {
        TextPipe pipe = new TextPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        assertTrue(pipe.closed());

        pipe = new TextPipe();
        assertFalse(pipe.closed());
        pipe.output().close();
        assertTrue(pipe.closed());

        pipe = new TextPipe();
        assertFalse(pipe.closed());
        pipe.input().close();
        pipe.output().close();
        assertTrue(pipe.closed());
    }

    @Test
    @DisplayName("interrupt")
    void testInterrupt() throws InterruptedException {
        TextPipe pipe = new TextPipe();
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
    class Input {

        @Test
        @DisplayName("pipe()")
        void testPipe() {
            TextPipe pipe = new TextPipe();
            @SuppressWarnings("resource")
            PipeReader input = pipe.input();
            assertSame(pipe, input.pipe());
        }

        @Test
        @DisplayName("read()")
        void testReadChar() throws IOException {
            testReadChar(s -> s);
            testReadChar(StringBuilder::new);
            testReadChar(StringBuffer::new);
            testReadChar(CharBuffer::wrap);
        }

        private void testReadChar(Function<String, CharSequence> mapper) throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            TextPipe pipe = new TextPipe();
            new Thread(() -> writeDataCharByChar(pipe, mapper.apply(SOURCE))).start();
            try (Reader input = pipe.input()) {
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            }
            assertEquals(SOURCE, output.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadCharArrayRange() throws IOException {
            testReadCharArrayRange(s -> s);
            testReadCharArrayRange(StringBuilder::new);
            testReadCharArrayRange(StringBuffer::new);
            testReadCharArrayRange(CharBuffer::wrap);
        }

        private void testReadCharArrayRange(Function<String, CharSequence> mapper) throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            TextPipe pipe = new TextPipe();
            new Thread(() -> writeDataInChunks(pipe, mapper.apply(SOURCE), 10)).start();
            try (Reader input = pipe.input()) {
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], 1, 5));

                assertEquals(0, input.read(new char[5], 0, 0));
                copy(input, output, 5);
            }
            assertEquals(SOURCE, output.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() throws IOException {
            String expected = SOURCE.substring(0, 5) + SOURCE.substring(11, SOURCE.length());
            StringWriter output = new StringWriter(expected.length());

            TextPipe pipe = new TextPipe();
            new Thread(() -> writeDataInChunks(pipe, SOURCE, 5)).start();
            try (Reader input = pipe.input()) {
                char[] data = new char[10];
                int len = input.read(data);
                assertEquals(5, len);
                output.write(data, 0, len);
                assertEquals(0, input.skip(0));
                assertThrows(IllegalArgumentException.class, () -> input.skip(-1));
                assertEquals(5, input.skip(10));
                assertEquals(1, input.skip(1));
                copy(input, output);
                assertEquals(0, input.skip(1));
            }
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        void testAvailable() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            new Thread(() -> writeDataInChunks(pipe, SOURCE, 5)).start();
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

        @Test
        @DisplayName("operations with write error")
        void testOperationsWithWriteError() throws IOException {
            TextPipe pipe = new TextPipe();
            try (Reader input = pipe.input()) {
                IOException error = new IOException();

                @SuppressWarnings("resource")
                PipeWriter output = pipe.output();
                output.flush();
                output.close(error);

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new char[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.ready()));

                output.close();

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new char[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.ready()));

                output.close(null);

                assertEquals(-1, input.read());
                assertEquals(-1, input.read(new char[5], 0, 5));
                assertEquals(0, input.skip(5));
                assertFalse(input.ready());
            }
        }

        @Nested
        @DisplayName("writer died")
        class WriterDied {

            @Test
            @DisplayName("read()")
            void testReadChar() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (Reader input = pipe.input()) {
                    // perform one read to consume the data
                    input.read();
                    IOException thrown = assertThrows(IOException.class, () -> {
                        input.read();
                    });
                    assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("read(char[], int, int)")
            void testReadCharArrayRange() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (Reader input = pipe.input()) {
                    // perform one read to consume the data
                    assertEquals(1, input.read(new char[5], 0, 5));
                    IOException thrown = assertThrows(IOException.class, () -> {
                        input.read(new char[5], 0, 5);
                    });
                    assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("skip(long)")
            void testSkip() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> writeAndDie(pipe.output())).start();
                try (Reader input = pipe.input()) {
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
    class Output {

        @Test
        @DisplayName("pipe()")
        void testPipe() {
            TextPipe pipe = new TextPipe();
            @SuppressWarnings("resource")
            PipeWriter output = pipe.output();
            assertSame(pipe, output.pipe());
        }

        @Test
        @DisplayName("write(int)")
        void testWriteInt() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    output.write(SOURCE.charAt(i));
                }
            }
            readLatch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write('*'));
        }

        @Test
        @DisplayName("write(char[], int, int)")
        void testWriteCharArrayRange() throws IOException, InterruptedException {
            char[] chars = SOURCE.toCharArray();

            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], 1, 5));

                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 10, SOURCE.length());
                    output.write(chars, index, to - index);
                    index = to;
                }
            }
            readLatch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write(chars));
        }

        @Test
        @DisplayName("write(String, int, int)")
        void testWriteStringRange() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", 1, 5));

                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 10, SOURCE.length());
                    output.write(SOURCE, index, to - index);
                    index = to;
                }
            }
            readLatch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write(SOURCE));
        }

        @Test
        @DisplayName("append(CharSequence)")
        void testAppendCharSequence() throws IOException, InterruptedException {
            testAppendCharSequence(s -> s);
            testAppendCharSequence(StringBuilder::new);
            testAppendCharSequence(StringBuffer::new);
            testAppendCharSequence(CharBuffer::wrap);
        }

        private void testAppendCharSequence(Function<String, CharSequence> mapper) throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 10, SOURCE.length());
                    output.append(mapper.apply(SOURCE.substring(index, to)));
                    index = to;
                }
                output.append(null);
            }
            readLatch.await();
            assertEquals(SOURCE + "null", result.get());

            assertClosed(() -> pipe.output().append(SOURCE));
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        void testAppendSubSequence() throws IOException, InterruptedException {
            testAppendSubSequence(s -> s);
            testAppendSubSequence(StringBuilder::new);
            testAppendSubSequence(StringBuffer::new);
            testAppendSubSequence(CharBuffer::wrap);
        }

        private void testAppendSubSequence(Function<String, CharSequence> mapper) throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", 1, 6));

                CharSequence csq = mapper.apply(SOURCE);
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + 10, SOURCE.length());
                    output.append(csq, index, to);
                    index = to;
                }
                output.append(null, 1, 3);
            }
            readLatch.await();
            assertEquals(SOURCE + "ul", result.get());

            assertClosed(() -> pipe.output().write(SOURCE, 0, 10));
        }

        @Test
        @DisplayName("append(char)")
        void testAppendChar() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe();
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, readLatch)).start();
            try (Writer output = pipe.output()) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    output.append(SOURCE.charAt(i));
                }
            }
            readLatch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write('*'));
        }

        @Test
        @DisplayName("flush()")
        void testFlush() throws IOException {
            TextPipe pipe = new TextPipe();
            try (Writer output = pipe.output()) {
                assertDoesNotThrow(output::flush);
            }
        }

        @Test
        @DisplayName("operations with read error")
        void testOperationsWithReadError() throws IOException {
            char[] chars = SOURCE.toCharArray();

            TextPipe pipe = new TextPipe();
            try (Writer output = pipe.output()) {
                IOException error = new IOException();
                pipe.input().close(error);

                assertSame(error, assertThrows(IOException.class, () -> output.write(chars[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.write(chars, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.write(SOURCE, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(SOURCE)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(SOURCE, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(chars[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.flush()));

                pipe.input().close();

                assertSame(error, assertThrows(IOException.class, () -> output.write(chars[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.write(chars, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.write(SOURCE, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(SOURCE)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(SOURCE, 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> output.append(chars[0])));
                assertSame(error, assertThrows(IOException.class, () -> output.flush()));

                pipe.input().close(null);

                assertClosed(() -> output.write(chars[0]));
                assertClosed(() -> output.write(chars, 0, 5));
                assertClosed(() -> output.write(SOURCE, 0, 5));
                assertClosed(() -> output.append(SOURCE));
                assertClosed(() -> output.append(SOURCE, 0, 5));
                assertClosed(() -> output.append(chars[0]));
                assertClosed(() -> output.flush());
            }
        }

        @Nested
        @DisplayName("parallel writes")
        class ParallelWrites {

            @Test
            @DisplayName("write(int)")
            void testWriteInt() throws InterruptedException {
                TextPipe pipe = new TextPipe();
                AtomicReference<String> result = new AtomicReference<>(null);
                CountDownLatch readLatch = new CountDownLatch(1);
                new Thread(() -> readAll(pipe, result, readLatch)).start();
                int threadCount = 3;
                CountDownLatch writeLatch = new CountDownLatch(threadCount);
                CountDownLatch closeLatch = new CountDownLatch(1);
                for (int i = 0; i < threadCount; i++) {
                    new Thread(() -> writeDataCharByChar(pipe.output(), SOURCE, writeLatch, closeLatch)).start();
                }
                writeLatch.await();
                pipe.output().close();
                closeLatch.countDown();
                readLatch.await();

                char[] expected = new char[SOURCE.length() * 3];
                SOURCE.getChars(0, SOURCE.length(), expected, 0);
                SOURCE.getChars(0, SOURCE.length(), expected, SOURCE.length());
                SOURCE.getChars(0, SOURCE.length(), expected, 2 * SOURCE.length());
                char[] actual = result.get().toCharArray();
                Arrays.sort(expected);
                Arrays.sort(actual);
                assertArrayEquals(expected, actual);

                assertClosed(() -> pipe.output().write(0));
            }

            @Test
            @DisplayName("write(char[], int, int)")
            void testWriteCharArrayRange() throws InterruptedException {
                TextPipe pipe = new TextPipe();
                AtomicReference<String> result = new AtomicReference<>(null);
                CountDownLatch readLatch = new CountDownLatch(1);
                new Thread(() -> readAll(pipe, result, readLatch)).start();
                int threadCount = 3;
                CountDownLatch writeLatch = new CountDownLatch(threadCount);
                CountDownLatch closeLatch = new CountDownLatch(1);
                for (int i = 0; i < threadCount; i++) {
                    new Thread(() -> writeDataInChunks(pipe.output(), SOURCE.toCharArray(), 10, writeLatch, closeLatch)).start();
                }
                writeLatch.await();
                pipe.output().close();
                closeLatch.countDown();
                readLatch.await();

                char[] expected = new char[SOURCE.length() * 3];
                SOURCE.getChars(0, SOURCE.length(), expected, 0);
                SOURCE.getChars(0, SOURCE.length(), expected, SOURCE.length());
                SOURCE.getChars(0, SOURCE.length(), expected, 2 * SOURCE.length());
                char[] actual = result.get().toCharArray();
                Arrays.sort(expected);
                Arrays.sort(actual);
                assertArrayEquals(expected, actual);

                assertClosed(() -> pipe.output().write(SOURCE));
            }

            @Test
            @DisplayName("append(CharSequence, int, int)")
            void testAppendSubSequence() throws InterruptedException {
                TextPipe pipe = new TextPipe();
                AtomicReference<String> result = new AtomicReference<>(null);
                CountDownLatch readLatch = new CountDownLatch(1);
                new Thread(() -> readAll(pipe, result, readLatch)).start();
                int threadCount = 3;
                CountDownLatch writeLatch = new CountDownLatch(threadCount);
                CountDownLatch closeLatch = new CountDownLatch(1);
                for (int i = 0; i < threadCount; i++) {
                    new Thread(() -> appendDataInChunks(pipe.output(), SOURCE, 10, writeLatch, closeLatch)).start();
                }
                writeLatch.await();
                pipe.output().close();
                closeLatch.countDown();
                readLatch.await();

                char[] expected = new char[SOURCE.length() * 3];
                SOURCE.getChars(0, SOURCE.length(), expected, 0);
                SOURCE.getChars(0, SOURCE.length(), expected, SOURCE.length());
                SOURCE.getChars(0, SOURCE.length(), expected, 2 * SOURCE.length());
                char[] actual = result.get().toCharArray();
                Arrays.sort(expected);
                Arrays.sort(actual);
                assertArrayEquals(expected, actual);

                assertClosed(() -> pipe.output().write(SOURCE));
            }
        }

        @Nested
        @DisplayName("reader died")
        class ReaderDied {

            @Test
            @DisplayName("write(int)")
            void testWriteInt() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(0);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("write(char[], int, int)")
            void testWriteCharArrayRange() throws IOException {
                char[] chars = SOURCE.toCharArray();

                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(chars, 0, chars.length);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("write(String, int, int)")
            void testWriteStringRange() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(SOURCE, 0, SOURCE.length());
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("append(CharSequence)")
            void testAppendCharSequence() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.append(SOURCE);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("append(CharSequence, int, int)")
            void testAppendSubSequence() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.append(SOURCE, 0, SOURCE.length());
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("append(char)")
            void testAppendChar() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.write(0);
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }

            @Test
            @DisplayName("flush()")
            void testFlush() throws IOException {
                TextPipe pipe = new TextPipe();
                new Thread(() -> skipAndDie(pipe.input())).start();
                try (Writer output = pipe.output()) {
                    output.write(0);
                    IOException thrown = assertThrows(IOException.class, () -> {
                        output.flush();
                    });
                    assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
                }
            }
        }
    }

    private void writeDataCharByChar(TextPipe pipe, CharSequence data) {
        try (PipeWriter output = pipe.output()) {
            for (int i = 0; i < data.length(); i++) {
                output.write(data.charAt(i));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDataCharByChar(PipeWriter output, CharSequence data, CountDownLatch writelatch, CountDownLatch closeLatch) {
        try {
            for (int i = 0; i < data.length(); i++) {
                output.write(data.charAt(i));
            }
            writelatch.countDown();
            closeLatch.await();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void writeDataInChunks(TextPipe pipe, CharSequence data, int chunkSize) {
        try (PipeWriter output = pipe.output()) {
            int index = 0;
            int remaining = data.length();
            while (remaining > 0) {
                int count = Math.min(remaining, chunkSize);
                appendData(data, output, index, count);
                index += count;
                remaining -= count;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDataInChunks(PipeWriter output, char[] data, int chunkSize, CountDownLatch writeLatch, CountDownLatch closeLatch) {
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

    private void appendDataInChunks(PipeWriter output, String data, int chunkSize, CountDownLatch writeLatch, CountDownLatch closeLatch) {
        try {
            int index = 0;
            int remaining = data.length();
            while (remaining > 0) {
                int count = Math.min(remaining, chunkSize);
                appendData(data, output, index, count);
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

    private void writeAndDie(PipeWriter output) {
        try {
            output.write(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readAll(TextPipe pipe, AtomicReference<String> result, CountDownLatch readLatch) {
        StringWriter output = new StringWriter();

        try (Reader input = pipe.input()) {
            int b;
            while ((b = input.read()) != -1) {
                output.write(b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.set(output.toString());
        readLatch.countDown();
    }

    private void skipAndDie(PipeReader input) {
        try {
            input.skip(Integer.MAX_VALUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("resource")
    private void appendData(CharSequence data, PipeWriter output, int index, int count) throws IOException {
        output.append(data, index, index + count);
    }
}
