/*
 * CharacterPipe.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkOffsetAndLength;
import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Deque;
import java.util.LinkedList;

/**
 * A class that pipes a reader and writer together.
 * This class behaves somewhat as a combination of {@link PipedReader} and {@link PipedWriter}, but the pipe will not be broken if the
 * writing thread is no longer alive. As with these streams it is not recommended to write to the pipe and read from the pipe from the same thread.
 *
 * @author Rob Spoor
 */
public final class CharacterPipe {

    private static final int MAX_ARRAY_BUFFER_CAPACITY = 1024;

    final Buffer buffer;
    private final int capacity;

    private final Reader input;
    private final Writer output;

    private boolean closed;

    /**
     * Creates a new character pipe with a capacity of {@code 1024}.
     */
    public CharacterPipe() {
        this(1024);
    }

    /**
     * Creates a new character pipe.
     *
     * @param capacity The capacity of the pipe's buffer.
     * @throws IllegalArgumentException If the given capacity ize is not at least {@code 1}.
     */
    public CharacterPipe(int capacity) {
        buffer = createBuffer(capacity);
        this.capacity = capacity;

        input = new PipeReader();
        output = new PipeWriter();

        closed = false;
    }

    private Buffer createBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(capacity + " < 1"); //$NON-NLS-1$
        }
        if (capacity == 1) {
            return new SingleValueBuffer();
        }
        return capacity <= MAX_ARRAY_BUFFER_CAPACITY ? new ArrayBuffer(capacity) : new DequeBuffer();
    }

    /**
     * Returns the pipe's reader.
     *
     * @return The pipe's reader.
     */
    public Reader input() {
        return input;
    }

    /**
     * Returns the pipe's writer.
     *
     * @return The pipe's writer.
     */
    public Writer output() {
        return output;
    }

    /**
     * Returns whether or not the pipe is closed. The pipe can be closed by either closing the {@link #input() input} or the {@link #output() output}.
     *
     * @return {@code true} if the pipe is closed, or {@code false} otherwise.
     */
    public boolean closed() {
        synchronized (buffer) {
            return closed;
        }
    }

    interface Buffer {

        int size();

        boolean isEmpty();

        char next();

        void add(char c);

        void clear();

        default void waitOrFail() throws IOException {
            try {
                wait();
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
                InterruptedIOException exception = new InterruptedIOException(e.getMessage());
                exception.initCause(e);
                throw exception;
            }
        }
    }

    static final class SingleValueBuffer implements Buffer {

        private boolean hasValue;
        private char value;

        @Override
        public int size() {
            return hasValue ? 1 : 0;
        }

        @Override
        public boolean isEmpty() {
            return !hasValue;
        }

        @Override
        public char next() {
            if (!hasValue) {
                throw new IllegalStateException("cannot take from empty buffer"); //$NON-NLS-1$
            }
            hasValue = false;
            return value;
        }

        @Override
        public void add(char c) {
            if (hasValue) {
                throw new IllegalStateException("cannot add to full buffer"); //$NON-NLS-1$
            }
            value = c;
            hasValue = true;
        }

        @Override
        public void clear() {
            hasValue = false;
        }
    }

    static final class ArrayBuffer implements Buffer {

        private final char[] buffer;
        private int first;
        private int last;
        private int size;

        ArrayBuffer(int capacity) {
            buffer = new char[capacity];
            first = 0;
            last = 0;
            size = 0;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public char next() {
            if (size == 0) {
                throw new IllegalStateException("cannot take from empty buffer"); //$NON-NLS-1$
            }
            char c = buffer[first];
            first = (first + 1) % buffer.length;
            size--;
            return c;
        }

        @Override
        public void add(char c) {
            if (size == buffer.length) {
                throw new IllegalStateException("cannot add to full buffer"); //$NON-NLS-1$
            }
            buffer[last] = c;
            last = (last + 1) % buffer.length;
            size++;
        }

        @Override
        public void clear() {
            first = 0;
            last = 0;
            size = 0;
        }
    }

    static final class DequeBuffer implements Buffer {

        private final Deque<Character> buffer = new LinkedList<>();

        @Override
        public int size() {
            return buffer.size();
        }

        @Override
        public boolean isEmpty() {
            return buffer.isEmpty();
        }

        @Override
        public char next() {
            return buffer.removeFirst();
        }

        @Override
        public void add(char c) {
            buffer.offerLast(c);
        }

        @Override
        public void clear() {
            buffer.clear();
        }
    }

    private class PipeReader extends Reader {

        @Override
        public int read() throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.isEmpty()) {
                    buffer.waitOrFail();
                }
                if (buffer.isEmpty()) {
                    // closed
                    return -1;
                }
                char c = buffer.next();
                buffer.notifyAll();
                return c & 0xFF;
            }
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            checkOffsetAndLength(cbuf, off, len);
            if (len == 0) {
                return 0;
            }
            synchronized (buffer) {
                while (!closed && buffer.isEmpty()) {
                    buffer.waitOrFail();
                }
                if (buffer.isEmpty()) {
                    // closed
                    return -1;
                }
                int i = 0;
                while (i < len && !buffer.isEmpty()) {
                    cbuf[off + i] = buffer.next();
                    i++;
                }
                buffer.notifyAll();
                return i;
            }
        }

        @Override
        public long skip(long n) throws IOException {
            if (n < 0) {
                throw new IllegalArgumentException(n + " < 0"); //$NON-NLS-1$
            }
            if (n == 0) {
                return 0;
            }
            synchronized (buffer) {
                long remaining = n;
                while (remaining > 0 && !buffer.isEmpty()) {
                    buffer.next();
                    remaining--;
                }
                buffer.notifyAll();
                return n - remaining;
            }
        }

        @Override
        public boolean ready() throws IOException {
            synchronized (buffer) {
                return !buffer.isEmpty();
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (buffer) {
                closed = true;
                buffer.clear();
                buffer.notifyAll();
            }
        }
    }

    private class PipeWriter extends Writer {

        @Override
        public void write(int c) throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.size() >= capacity) {
                    buffer.waitOrFail();
                }
                if (closed) {
                    throw new IOException(Messages.stream.closed.get());
                }
                buffer.add((char) c);
                buffer.notifyAll();
            }
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            checkOffsetAndLength(cbuf, off, len);
            int index = off;
            int remaining = len;
            while (remaining > 0) {
                // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
                int count = Math.min(remaining, capacity);
                writeChars(cbuf, index, count);
                index += count;
                remaining -= count;
            }
        }

        private void writeChars(char[] cbuf, int off, int len) throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.size() > capacity - len) {
                    buffer.waitOrFail();
                }
                if (closed) {
                    throw new IOException(Messages.stream.closed.get());
                }
                for (int i = off, j = 0; j < len; i++, j++) {
                    buffer.add(cbuf[i]);
                }
                buffer.notifyAll();
            }
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            checkOffsetAndLength(str, off, len);
            int index = off;
            int remaining = len;
            while (remaining > 0) {
                // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
                int count = Math.min(remaining, capacity);
                writeChars(str, index, count);
                index += count;
                remaining -= count;
            }
        }

        @Override
        public Writer append(CharSequence csq) throws IOException {
            CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
            return append(cs, 0, cs.length());
        }

        @Override
        public Writer append(CharSequence csq, int start, int end) throws IOException {
            CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
            checkStartAndEnd(cs, start, end);
            int index = start;
            int remaining = end - start;
            while (remaining > 0) {
                // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
                int count = Math.min(remaining, capacity);
                writeChars(cs, index, count);
                index += count;
                remaining -= count;
            }
            return this;
        }

        private void writeChars(CharSequence csq, int off, int len) throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.size() > capacity - len) {
                    buffer.waitOrFail();
                }
                if (closed) {
                    throw new IOException(Messages.stream.closed.get());
                }
                for (int i = off, j = 0; j < len; i++, j++) {
                    buffer.add(csq.charAt(i));
                }
                buffer.notifyAll();
            }
        }

        @Override
        public void flush() throws IOException {
            // does nothing
        }

        @Override
        public void close() throws IOException {
            synchronized (buffer) {
                closed = true;
                // don't clear the buffer, the input stream may still be active
                buffer.notifyAll();
            }
        }
    }
}
