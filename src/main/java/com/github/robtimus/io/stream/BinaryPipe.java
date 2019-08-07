/*
 * BinaryPipe.java
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Deque;
import java.util.LinkedList;

/**
 * A class that pipes an input stream and output stream together.
 * This class behaves somewhat as a combination of {@link PipedInputStream} and {@link PipedOutputStream}, but the pipe will not be broken if the
 * writing thread is no longer alive. As with these streams it is not recommended to write to the pipe and read from the pipe from the same thread.
 *
 * @author Rob Spoor
 */
public final class BinaryPipe {

    private static final int MAX_ARRAY_BUFFER_CAPACITY = 1024;

    final Buffer buffer;
    private final int capacity;

    private final InputStream input;
    private final OutputStream output;

    private boolean closed;

    /**
     * Creates a new binary pipe with a capacity of {@code 1024}.
     */
    public BinaryPipe() {
        this(1024);
    }

    /**
     * Creates a new binary pipe.
     *
     * @param capacity The capacity of the pipe's buffer.
     * @throws IllegalArgumentException If the given capacity ize is not at least {@code 1}.
     */
    public BinaryPipe(int capacity) {
        buffer = createBuffer(capacity);
        this.capacity = capacity;

        input = new PipeInputStream();
        output = new PipeOutputStream();

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
     * Returns the pipe's input stream.
     *
     * @return The pipe's input stream.
     */
    public InputStream input() {
        return input;
    }

    /**
     * Returns the pipe's output stream.
     *
     * @return The pipe's output stream.
     */
    public OutputStream output() {
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

        byte next();

        void add(byte b);

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
        private byte value;

        @Override
        public int size() {
            return hasValue ? 1 : 0;
        }

        @Override
        public boolean isEmpty() {
            return !hasValue;
        }

        @Override
        public byte next() {
            if (!hasValue) {
                throw new IllegalStateException("cannot take from empty buffer"); //$NON-NLS-1$
            }
            hasValue = false;
            return value;
        }

        @Override
        public void add(byte b) {
            if (hasValue) {
                throw new IllegalStateException("cannot add to full buffer"); //$NON-NLS-1$
            }
            value = b;
            hasValue = true;
        }

        @Override
        public void clear() {
            hasValue = false;
        }
    }

    static final class ArrayBuffer implements Buffer {

        private final byte[] buffer;
        private int first;
        private int last;
        private int size;

        ArrayBuffer(int capacity) {
            buffer = new byte[capacity];
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
        public byte next() {
            if (size == 0) {
                throw new IllegalStateException("cannot take from empty buffer"); //$NON-NLS-1$
            }
            byte b = buffer[first];
            first = (first + 1) % buffer.length;
            size--;
            return b;
        }

        @Override
        public void add(byte b) {
            if (size == buffer.length) {
                throw new IllegalStateException("cannot add to full buffer"); //$NON-NLS-1$
            }
            buffer[last] = b;
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

        private final Deque<Byte> buffer = new LinkedList<>();

        @Override
        public int size() {
            return buffer.size();
        }

        @Override
        public boolean isEmpty() {
            return buffer.isEmpty();
        }

        @Override
        public byte next() {
            return buffer.removeFirst();
        }

        @Override
        public void add(byte b) {
            buffer.offerLast(b);
        }

        @Override
        public void clear() {
            buffer.clear();
        }
    }

    private class PipeInputStream extends InputStream {

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
                byte b = buffer.next();
                buffer.notifyAll();
                return b & 0xFF;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkOffsetAndLength(b, off, len);
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
                    b[off + i] = buffer.next();
                    i++;
                }
                buffer.notifyAll();
                return i;
            }
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) {
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
        public int available() throws IOException {
            synchronized (buffer) {
                return buffer.size();
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

    private class PipeOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.size() >= capacity) {
                    buffer.waitOrFail();
                }
                if (closed) {
                    throw new IOException(Messages.stream.closed.get());
                }
                buffer.add((byte) b);
                buffer.notifyAll();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            checkOffsetAndLength(b, off, len);
            int index = off;
            int remaining = len;
            while (remaining > 0) {
                // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
                int count = Math.min(remaining, capacity);
                writeBytes(b, index, count);
                index += count;
                remaining -= count;
            }
        }

        private void writeBytes(byte[] b, int off, int len) throws IOException {
            synchronized (buffer) {
                while (!closed && buffer.size() > capacity - len) {
                    buffer.waitOrFail();
                }
                if (closed) {
                    throw new IOException(Messages.stream.closed.get());
                }
                for (int i = off, j = 0; j < len; i++, j++) {
                    buffer.add(b[i]);
                }
                buffer.notifyAll();
            }
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
