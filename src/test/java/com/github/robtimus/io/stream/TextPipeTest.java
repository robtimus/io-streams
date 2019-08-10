/*
 * TextPipeTest.java
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class TextPipeTest extends TestBase {

    @Test
    @DisplayName("TestPipe(int)")
    public void testConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new TextPipe(-1));
        assertThrows(IllegalArgumentException.class, () -> new TextPipe(0));
    }

    @Test
    @DisplayName("closed()")
    public void testClosed() {
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
    public void testInterrupt() throws InterruptedException {
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
    public class Input {

        private void writeDataCharByChar(TextPipe pipe, String data) {
            try (PipeWriter output = pipe.output()) {
                writeDataCharByChar(output, data);
            }
        }

        private void writeDataCharByChar(PipeWriter output, String data) {
            try {
                Thread.sleep(100);
                for (int i = 0; i < data.length(); i++) {
                    output.write(data.charAt(i));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private void writeDataInChunks(TextPipe pipe, String data, int chunkSize) {
            try (PipeWriter output = pipe.output()) {
                writeDataInChunks(output, data, chunkSize);
            }
        }

        private void writeDataInChunks(PipeWriter output, String data, int chunkSize) {
            try {
                Thread.sleep(100);
                int index = 0;
                int remaining = data.length();
                while (remaining > 0) {
                    int count = Math.min(remaining, chunkSize);
                    output.write(data, index, count);
                    index += count;
                    remaining -= count;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Test
        @DisplayName("pipe()")
        public void testPipe() {
            TextPipe pipe = new TextPipe(1);
            assertSame(pipe, pipe.input().pipe());
        }

        @Test
        @DisplayName("read()")
        public void testReadByte() throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            TextPipe pipe = new TextPipe(1);
            new Thread(() -> writeDataCharByChar(pipe, SOURCE)).start();
            try (Reader input = pipe.input()) {
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            }
            assertEquals(SOURCE, output.toString());
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        public void testReadByteArrayRange() throws IOException {
            testReadByteArrayRange(3, 5);
            testReadByteArrayRange(5, 3);
        }

        private void testReadByteArrayRange(int capacity, int chunkSize) throws IOException {
            StringWriter output = new StringWriter(SOURCE.length());

            TextPipe pipe = new TextPipe(capacity);
            new Thread(() -> writeDataInChunks(pipe, SOURCE, capacity)).start();
            try (Reader input = pipe.input()) {
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> input.read(new char[5], 1, 5));

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

            TextPipe pipe = new TextPipe(5);
            new Thread(() -> writeDataInChunks(pipe, SOURCE, 5)).start();
            try (Reader input = pipe.input()) {
                char[] data = new char[10];
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
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("ready()")
        public void testAvailable() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(5);
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
        public void testOperationsWithWriteError() throws IOException {
            TextPipe pipe = new TextPipe();
            try (Reader input = pipe.input()) {
                IOException error = new IOException();
                pipe.output().write(SOURCE);
                pipe.output().close(error);

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new char[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.ready()));

                pipe.output().close();

                assertSame(error, assertThrows(IOException.class, () -> input.read()));
                assertSame(error, assertThrows(IOException.class, () -> input.read(new char[5], 0, 5)));
                assertSame(error, assertThrows(IOException.class, () -> input.skip(5)));
                assertSame(error, assertThrows(IOException.class, () -> input.ready()));

                pipe.output().close(null);

                assertEquals(SOURCE.charAt(0), input.read());
                assertEquals(5, input.read(new char[5], 0, 5));
                assertEquals(5, input.skip(5));
                assertEquals(SOURCE.charAt(11), input.read());
                assertTrue(input.ready());
            }
        }

        @Nested
        @DisplayName("writer died")
        public class WriterDied {

            @Test
            @DisplayName("read()")
            public void testReadByte() {
                StringWriter output = new StringWriter(SOURCE.length());

                TextPipe pipe = new TextPipe(1);
                new Thread(() -> writeDataCharByChar(pipe.output(), SOURCE)).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Reader input = pipe.input()) {
                        int c;
                        while ((c = input.read()) != -1) {
                            output.write(c);
                        }
                    }
                });
                assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("read(byte[], int, int)")
            public void testReadByteArrayRange() {
                testReadByteArrayRange(3, 5);
                testReadByteArrayRange(5, 3);
            }

            private void testReadByteArrayRange(int capacity, int chunkSize) {
                StringWriter output = new StringWriter(SOURCE.length());

                TextPipe pipe = new TextPipe(capacity);
                new Thread(() -> writeDataInChunks(pipe.output(), SOURCE, capacity)).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Reader input = pipe.input()) {
                        assertEquals(0, input.read(new char[5], 0, 0));
                        copy(input, output, chunkSize);
                    }
                });
                assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("skip(long)")
            public void testSkip() {
                TextPipe pipe = new TextPipe(5);
                new Thread(() -> writeDataInChunks(pipe.output(), SOURCE, 5)).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Reader input = pipe.input()) {
                        while (true) {
                            input.skip(1);
                        }
                    }
                });
                assertEquals(Messages.pipe.writerDied.get(), thrown.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("output()")
    public class Output {

        @Test
        @DisplayName("pipe()")
        public void testPipe() {
            TextPipe pipe = new TextPipe(1);
            assertSame(pipe, pipe.output().pipe());
        }

        private void readAll(TextPipe pipe, AtomicReference<String> result, CountDownLatch latch) {
            StringWriter output = new StringWriter();

            try (Reader input = pipe.input()) {
                Thread.sleep(100);
                int b;
                while ((b = input.read()) != -1) {
                    output.write(b);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            result.set(output.toString());
            latch.countDown();
        }

        private void skipAndDie(PipeReader input) {
            try {
                input.skip(Integer.MAX_VALUE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Test
        @DisplayName("write(int)")
        public void testWriteInt() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(1);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    output.write(SOURCE.charAt(i));
                }
            }
            latch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write('*'));
        }

        @Test
        @DisplayName("write(char[], int, int)")
        public void testWriteCharArrayRange() throws IOException, InterruptedException {
            testWriteCharArrayRange(5, 5);
            testWriteCharArrayRange(3, 5);
            testWriteCharArrayRange(5, 3);
        }

        private void testWriteCharArrayRange(int capacity, int chunkSize) throws IOException, InterruptedException {
            char[] chars = SOURCE.toCharArray();

            TextPipe pipe = new TextPipe(capacity);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write(new char[5], 1, 5));

                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.write(chars, index, to - index);
                    index = to;
                }
            }
            latch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write(chars));
        }

        @Test
        @DisplayName("write(String, int, int)")
        public void testWriteStringRange() throws IOException, InterruptedException {
            testWriteStringRange(5, 5);
            testWriteStringRange(3, 5);
            testWriteStringRange(5, 3);
        }

        private void testWriteStringRange(int capacity, int chunkSize) throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(capacity);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.write("12345", 1, 5));

                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.write(SOURCE, index, to - index);
                    index = to;
                }
            }
            latch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write(SOURCE));
        }

        @Test
        @DisplayName("append(CharSequence)")
        public void testAppendCharSequence() throws IOException, InterruptedException {
            testAppendCharSequence(5, 5);
            testAppendCharSequence(3, 5);
            testAppendCharSequence(5, 3);
        }

        private void testAppendCharSequence(int capacity, int chunkSize) throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(capacity);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.append(SOURCE.substring(index, to));
                    index = to;
                }
                output.append(null);
            }
            latch.await();
            assertEquals(SOURCE + "null", result.get());

            assertClosed(() -> pipe.output().append(SOURCE));
        }

        @Test
        @DisplayName("append(CharSequence, int, int)")
        public void testAppendSubSequence() throws IOException, InterruptedException {
            testAppendSubSequence(5, 5);
            testAppendSubSequence(3, 5);
            testAppendSubSequence(5, 3);
        }

        private void testAppendSubSequence(int capacity, int chunkSize) throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(capacity);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", -1, 5));
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", 0, -1));
                assertThrows(IndexOutOfBoundsException.class, () -> output.append("12345", 1, 6));

                int index = 0;
                while (index < SOURCE.length()) {
                    int to = Math.min(index + chunkSize, SOURCE.length());
                    output.append(SOURCE, index, to);
                    index = to;
                }
                output.append(null, 1, 3);
            }
            latch.await();
            assertEquals(SOURCE + "ul", result.get());

            assertClosed(() -> pipe.output().write(SOURCE, 0, 10));
        }

        @Test
        @DisplayName("append(char)")
        public void testAppendChar() throws IOException, InterruptedException {
            TextPipe pipe = new TextPipe(1);
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> readAll(pipe, result, latch)).start();
            try (Writer output = pipe.output()) {
                for (int i = 0; i < SOURCE.length(); i++) {
                    output.append(SOURCE.charAt(i));
                }
            }
            latch.await();
            assertEquals(SOURCE, result.get());

            assertClosed(() -> pipe.output().write('*'));
        }

        @Test
        @DisplayName("flush()")
        public void testFlush() throws IOException {
            TextPipe pipe = new TextPipe(1);
            try (Writer output = pipe.output()) {
                output.flush();
            }
        }

        @Test
        @DisplayName("operations with read error")
        public void testOperationsWithReadError() throws IOException {
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
        @DisplayName("reader died")
        public class ReaderDied {

            @Test
            @DisplayName("write(int)")
            public void testWriteInt() {
                TextPipe pipe = new TextPipe(1);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        for (int i = 0; i < SOURCE.length(); i++) {
                            output.write(SOURCE.charAt(i));
                        }
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("write(char[], int, int)")
            public void testWriteCharArrayRange() {
                testWriteCharArrayRange(5, 5);
                testWriteCharArrayRange(3, 5);
                testWriteCharArrayRange(5, 3);
            }

            private void testWriteCharArrayRange(int capacity, int chunkSize) {
                char[] chars = SOURCE.toCharArray();

                TextPipe pipe = new TextPipe(capacity);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        int index = 0;
                        while (index < SOURCE.length()) {
                            int to = Math.min(index + chunkSize, SOURCE.length());
                            output.write(chars, index, to - index);
                            index = to;
                        }
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("write(String, int, int)")
            public void testWriteStringRange() {
                testWriteStringRange(5, 5);
                testWriteStringRange(3, 5);
                testWriteStringRange(5, 3);
            }

            private void testWriteStringRange(int capacity, int chunkSize) {
                TextPipe pipe = new TextPipe(capacity);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        int index = 0;
                        while (index < SOURCE.length()) {
                            int to = Math.min(index + chunkSize, SOURCE.length());
                            output.write(SOURCE, index, to - index);
                            index = to;
                        }
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("append(CharSequence)")
            public void testAppendCharSequence() {
                testAppendCharSequence(5, 5);
                testAppendCharSequence(3, 5);
                testAppendCharSequence(5, 3);
            }

            private void testAppendCharSequence(int capacity, int chunkSize) {
                TextPipe pipe = new TextPipe(capacity);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        int index = 0;
                        while (index < SOURCE.length()) {
                            int to = Math.min(index + chunkSize, SOURCE.length());
                            output.append(SOURCE.substring(index, to));
                            index = to;
                        }
                        output.append(null);
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("append(CharSequence, int, int)")
            public void testAppendSubSequence() {
                testAppendSubSequence(5, 5);
                testAppendSubSequence(3, 5);
                testAppendSubSequence(5, 3);
            }

            private void testAppendSubSequence(int capacity, int chunkSize) {
                TextPipe pipe = new TextPipe(capacity);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        int index = 0;
                        while (index < SOURCE.length()) {
                            int to = Math.min(index + chunkSize, SOURCE.length());
                            output.append(SOURCE, index, to);
                            index = to;
                        }
                        output.append(null, 1, 3);
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("append(char)")
            public void testAppendChar() {
                TextPipe pipe = new TextPipe(1);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        for (int i = 0; i < SOURCE.length(); i++) {
                            output.append(SOURCE.charAt(i));
                        }
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }

            @Test
            @DisplayName("flush()")
            public void testFlush() {
                TextPipe pipe = new TextPipe(1);
                new Thread(() -> skipAndDie(pipe.input())).start();
                IOException thrown = assertThrows(IOException.class, () -> {
                    try (Writer output = pipe.output()) {
                        while (true) {
                            output.flush();
                        }
                    }
                });
                assertEquals(Messages.pipe.readerDied.get(), thrown.getMessage());
            }
        }
    }
}
