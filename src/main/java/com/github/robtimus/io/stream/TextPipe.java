/*
 * TextPipe.java
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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class that pipes a reader and writer together.
 * This class behaves somewhat as a combination of {@link PipedReader} and {@link PipedWriter}, but the pipe will not be broken if the writing thread
 * is no longer alive. As with these streams it is not recommended to write to the pipe and read from the pipe from the same thread.
 * <p>
 * In addition, it's possible to pass an {@link IOException} from the input to the output or vice versa, by using
 * {@link PipeReader#close(IOException)} or {@link PipeWriter#close(IOException)}.
 *
 * @author Rob Spoor
 */
public final class TextPipe {

    private static final int DEFAULT_PIPE_SIZE = 1024;
    private static final long AWAIT_TIME = TimeUnit.SECONDS.toNanos(1);

    private final PipeReader input;
    private final PipeWriter output;

    private final char[] buffer;
    private int first;
    private int last;
    private int size;

    private final Lock lock;
    private final Condition closedOrNotEmpty;
    private final Condition closedOrNotFull;

    private boolean closed;
    private IOException readError;
    private IOException writeError;

    private Thread readThread;
    private Thread writeThread;

    /**
     * Creates a new text pipe with a capacity of {@code 1024}.
     */
    public TextPipe() {
        this(DEFAULT_PIPE_SIZE);
    }

    /**
     * Creates a new text pipe.
     *
     * @param capacity The capacity of the pipe's buffer.
     * @throws IllegalArgumentException If the given capacity is not at least {@code 1}.
     */
    public TextPipe(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(capacity + " < 1"); //$NON-NLS-1$
        }

        buffer = new char[capacity];
        first = 0;
        last = 0;
        size = 0;

        lock = new ReentrantLock(true);
        closedOrNotEmpty = lock.newCondition();
        closedOrNotFull = lock.newCondition();

        closed = false;
        readError = null;
        writeError = null;

        input = new PipeReader(this);
        output = new PipeWriter(this);
    }

    /**
     * Returns the pipe's reader.
     *
     * @return The pipe's reader.
     */
    public PipeReader input() {
        return input;
    }

    /**
     * Returns the pipe's writer.
     *
     * @return The pipe's writer.
     */
    public PipeWriter output() {
        return output;
    }

    /**
     * Returns whether or not the pipe is closed. The pipe can be closed by either closing the {@link #input() input} or the {@link #output() output}.
     *
     * @return {@code true} if the pipe is closed, or {@code false} otherwise.
     */
    public boolean closed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    // input methods

    int read() throws IOException {
        lock.lock();
        try {
            readThread = Thread.currentThread();
            while (!closed && size == 0 && !writerDied()) {
                await(closedOrNotEmpty);
            }
            throwWriteError();
            if (size > 0) {
                char c = next();
                closedOrNotFull.signalAll();
                return c & 0xFF;
            }
            if (closed) {
                return -1;
            }
            throw writerDiedException();
        } finally {
            lock.unlock();
        }
    }

    int read(char[] cbuf, int off, int len) throws IOException {
        checkOffsetAndLength(cbuf, off, len);
        if (len == 0) {
            return 0;
        }
        lock.lock();
        try {
            readThread = Thread.currentThread();
            while (!closed && size == 0 && !writerDied()) {
                await(closedOrNotEmpty);
            }
            throwWriteError();
            if (size > 0) {
                int i = 0;
                while (i < len && size > 0) {
                    cbuf[off + i] = next();
                    i++;
                }
                closedOrNotFull.signalAll();
                return i;
            }
            if (closed) {
                return -1;
            }
            throw writerDiedException();
        } finally {
            lock.unlock();
        }
    }

    private char next() {
        assert size > 0 : "cannot take from empty buffer"; //$NON-NLS-1$
        char c = buffer[first];
        first = (first + 1) % buffer.length;
        size--;
        return c;
    }

    long skip(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException(n + " < 0"); //$NON-NLS-1$
        }
        if (n == 0) {
            return 0;
        }
        lock.lock();
        try {
            readThread = Thread.currentThread();
            throwWriteError();
            if (size > 0) {
                long remaining = n;
                while (remaining > 0 && size > 0) {
                    next();
                    remaining--;
                }
                closedOrNotFull.signalAll();
                return n - remaining;
            }
            if (closed || !writerDied()) {
                return 0;
            }
            throw writerDiedException();
        } finally {
            lock.unlock();
        }
    }

    boolean ready() throws IOException {
        lock.lock();
        try {
            readThread = Thread.currentThread();
            throwWriteError();
            return size > 0;
        } finally {
            lock.unlock();
        }
    }

    void closeInput() {
        lock.lock();
        try {
            clear();
            closed = true;
            closedOrNotEmpty.signalAll();
            closedOrNotFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void closeInput(IOException error) {
        lock.lock();
        try {
            clear();
            closed = true;
            readError = error;
            closedOrNotEmpty.signalAll();
            closedOrNotFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void throwWriteError() throws IOException {
        if (writeError != null) {
            throw writeError;
        }
    }

    private boolean writerDied() {
        return writeThread != null && !writeThread.isAlive();
    }

    private IOException writerDiedException() {
        return new IOException(Messages.pipe.writerDied.get());
    }

    // output methods

    void write(int b) throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && size >= buffer.length && !readerDied()) {
                await(closedOrNotFull);
            }
            throwReadError();
            throwIfClosed();
            if (size < buffer.length) {
                add((char) b);
                closedOrNotEmpty.signalAll();
                return;
            }
            throw readerDiedException();
        } finally {
            lock.unlock();
        }
    }

    void write(char[] cbuf, int off, int len) throws IOException {
        checkOffsetAndLength(cbuf, off, len);
        int index = off;
        int remaining = len;
        while (remaining > 0) {
            // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
            int count = Math.min(remaining, buffer.length);
            writeChars(cbuf, index, count);
            index += count;
            remaining -= count;
        }
    }

    private void writeChars(char[] cbuf, int off, int len) throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && size > buffer.length - len && !readerDied()) {
                await(closedOrNotFull);
            }
            throwReadError();
            throwIfClosed();
            if (size <= buffer.length - len) {
                for (int i = off, j = 0; j < len; i++, j++) {
                    add(cbuf[i]);
                }
                closedOrNotEmpty.signalAll();
                return;
            }
            throw readerDiedException();
        } finally {
            lock.unlock();
        }
    }

    void write(String str, int off, int len) throws IOException {
        checkOffsetAndLength(str, off, len);
        int index = off;
        int remaining = len;
        while (remaining > 0) {
            // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
            int count = Math.min(remaining, buffer.length);
            writeChars(str, index, count);
            index += count;
            remaining -= count;
        }
    }

    void append(CharSequence csq) throws IOException {
        CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
        append(cs, 0, cs.length());
    }

    void append(CharSequence csq, int start, int end) throws IOException {
        CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
        checkStartAndEnd(cs, start, end);
        int index = start;
        int remaining = end - start;
        while (remaining > 0) {
            // write in chunks of at most the buffer's capacity, so the buffer will never run out of capacity
            int count = Math.min(remaining, buffer.length);
            writeChars(cs, index, count);
            index += count;
            remaining -= count;
        }
    }

    private void writeChars(CharSequence csq, int off, int len) throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && size > buffer.length - len && !readerDied()) {
                await(closedOrNotFull);
            }
            throwReadError();
            throwIfClosed();
            if (size <= buffer.length - len) {
                for (int i = off, j = 0; j < len; i++, j++) {
                    add(csq.charAt(i));
                }
                closedOrNotEmpty.signalAll();
                return;
            }
            throw readerDiedException();
        } finally {
            lock.unlock();
        }
    }

    void add(char c) {
        assert size < buffer.length : "cannot add to full buffer"; //$NON-NLS-1$
        buffer[last] = c;
        last = (last + 1) % buffer.length;
        size++;
    }

    void flush() throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            throwReadError();
            throwIfClosed();
            throwIfReaderDied();
        } finally {
            lock.unlock();
        }
    }

    void closeOutput() {
        lock.lock();
        try {
            // don't clear the buffer, the input stream may still be active
            closed = true;
            closedOrNotEmpty.signalAll();
            closedOrNotFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void closeOutput(IOException error) {
        lock.lock();
        try {
            // don't clear the buffer, the input stream may still be active
            closed = true;
            writeError = error;
            closedOrNotEmpty.signalAll();
            closedOrNotFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void throwReadError() throws IOException {
        if (readError != null) {
            throw readError;
        }
    }

    private void throwIfClosed() throws IOException {
        if (closed) {
            throw new IOException(Messages.stream.closed.get());
        }
    }

    private void throwIfReaderDied() throws IOException {
        if (readerDied()) {
            throw readerDiedException();
        }
    }

    private boolean readerDied() {
        return readThread != null && !readThread.isAlive();
    }

    private IOException readerDiedException() {
        return new IOException(Messages.pipe.readerDied.get());
    }

    // general purpose methods

    private void checkOffsetAndLength(char[] array, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidOffsetOrLength.get(array.length, offset, length));
        }
    }

    private void checkOffsetAndLength(CharSequence csq, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > csq.length()) {
            throw new ArrayIndexOutOfBoundsException(Messages.charSequence.invalidOffsetOrLength.get(csq.length(), offset, length));
        }
    }

    private void checkStartAndEnd(CharSequence sequence, int start, int end) {
        if (start < 0 || end > sequence.length() || start > end) {
            throw new ArrayIndexOutOfBoundsException(Messages.charSequence.invalidStartOrEnd.get(sequence.length(), start, end));
        }
    }

    private void clear() {
        first = 0;
        last = 0;
        size = 0;
    }

    private void await(Condition condition) throws IOException {
        try {
            condition.awaitNanos(AWAIT_TIME);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            InterruptedIOException exception = new InterruptedIOException(e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }
}
