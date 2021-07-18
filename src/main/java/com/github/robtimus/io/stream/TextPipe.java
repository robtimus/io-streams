/*
 * TextPipe.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkOffsetAndLength;
import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An in-memory pipe. It can be used to connect code expecting a reader with code expecting a writer.
 * <p>
 * This is a port of Go's <a href="https://golang.org/pkg/io/#Pipe">io.Pipe</a>. Like {@code io.Pipe}, each write to a {@link PipedWriter} blocks
 * until all of its data has been consumed, either through reads or skips. Data is copied directly from the writer to the reader without any internal
 * buffering.
 * <p>
 * Because reads and writes both block, writing to and reading from the pipe should not be done from the same thread. Attempting to do so will
 * introduce deadlocks.
 *
 * @author Rob Spoor
 */
public final class TextPipe {

    private static final long AWAIT_TIME = TimeUnit.SECONDS.toNanos(1);

    private final PipeReader input;
    private final PipeWriter output;

    private final char[] single;
    private Data data;
    private ArrayData arrayData;
    private CharSequenceData charSequenceData;
    private int start;
    private int end;

    private final Lock lock;
    private final Condition closedOrNotEmpty;
    private final Condition closedOrEmpty;

    private boolean closed;
    private IOException readError;
    private IOException writeError;

    private Thread readThread;
    private Thread writeThread;

    /**
     * Creates a new text pipe.
     */
    public TextPipe() {
        single = new char[1];

        lock = new ReentrantLock();
        closedOrNotEmpty = lock.newCondition();
        closedOrEmpty = lock.newCondition();

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
            while (!closed && start == end && !writerDied()) {
                await(closedOrNotEmpty);
            }
            throwWriteError();
            if (start < end) {
                char c = data.charAt(start++);
                checkEmpty();
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
            while (!closed && start == end && !writerDied()) {
                await(closedOrNotEmpty);
            }
            throwWriteError();
            if (start < end) {
                int result = Math.min(len, end - start);
                data.copyTo(start, cbuf, off, result);
                start += result;
                checkEmpty();
                return result;
            }
            if (closed) {
                return -1;
            }
            throw writerDiedException();
        } finally {
            lock.unlock();
        }
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
            while (!closed && start == end && !writerDied()) {
                await(closedOrNotEmpty);
            }
            throwWriteError();
            if (start < end) {
                long result = Math.min(n, (long) end - start);
                start += result;
                checkEmpty();
                return result;
            }
            if (closed) {
                return 0;
            }
            throw writerDiedException();
        } finally {
            lock.unlock();
        }
    }

    private void checkEmpty() {
        if (start == end) {
            data = null;
            closedOrEmpty.signalAll();
        }
    }

    boolean ready() throws IOException {
        lock.lock();
        try {
            readThread = Thread.currentThread();
            throwWriteError();
            return start < end;
        } finally {
            lock.unlock();
        }
    }

    void closeInput() {
        lock.lock();
        try {
            data = null;
            start = 0;
            end = 0;
            closed = true;
            closedOrNotEmpty.signalAll();
            closedOrEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void closeInput(IOException error) {
        lock.lock();
        try {
            data = null;
            start = 0;
            end = 0;
            closed = true;
            readError = error;
            closedOrNotEmpty.signalAll();
            closedOrEmpty.signalAll();
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

    void write(int c) throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && start < end && !readerDied()) {
                await(closedOrEmpty);
            }
            throwReadError();
            throwIfClosed();
            if (start == end) {
                single[0] = (char) c;
                data = arrayData(single);
                start = 0;
                end = 1;
                closedOrNotEmpty.signalAll();
            }
            awaitDataRead();
        } finally {
            lock.unlock();
        }
    }

    void write(char[] cbuf, int off, int len) throws IOException {
        checkOffsetAndLength(cbuf, off, len);
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && start < end && !readerDied()) {
                await(closedOrEmpty);
            }
            throwReadError();
            throwIfClosed();
            if (start == end) {
                data = arrayData(cbuf);
                start = off;
                end = off + len;
                closedOrNotEmpty.signalAll();
            }
            awaitDataRead();
        } finally {
            lock.unlock();
        }
    }

    private Data arrayData(char[] array) {
        if (arrayData == null) {
            arrayData = new ArrayData();
        }
        arrayData.array = array;
        return arrayData;
    }

    void write(String str, int off, int len) throws IOException {
        checkOffsetAndLength(str, off, len);
        writeChars(str, off, len);
    }

    void append(CharSequence csq) throws IOException {
        CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
        writeChars(cs, 0, cs.length());
    }

    void append(CharSequence csq, int start, int end) throws IOException {
        CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
        checkStartAndEnd(cs, start, end);
        writeChars(cs, start, end - start);
    }

    private void writeChars(CharSequence csq, int off, int len) throws IOException {
        lock.lock();
        try {
            writeThread = Thread.currentThread();
            while (!closed && start < end && !readerDied()) {
                await(closedOrEmpty);
            }
            throwReadError();
            throwIfClosed();
            if (start == end) {
                data = charSequenceData(csq);
                start = off;
                end = off + len;
                closedOrNotEmpty.signalAll();
            }
            awaitDataRead();
        } finally {
            lock.unlock();
        }
    }

    private Data charSequenceData(CharSequence csq) {
        if (charSequenceData == null) {
            charSequenceData = new CharSequenceData();
        }
        charSequenceData.charSequence = csq;
        return charSequenceData;
    }

    private void awaitDataRead() throws IOException {
        while (!closed && start < end && !readerDied()) {
            await(closedOrEmpty);
        }
        throwReadError();
        throwIfClosed();
        if (start < end) {
            throw readerDiedException();
        }
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
            // don't clear the data, the reader may still be active
            closed = true;
            closedOrNotEmpty.signalAll();
            closedOrEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void closeOutput(IOException error) {
        lock.lock();
        try {
            // don't clear the data, the reader may still be active
            closed = true;
            writeError = error;
            closedOrNotEmpty.signalAll();
            closedOrEmpty.signalAll();
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

    private void await(Condition condition) throws IOException {
        try {
            // if this call waits shorter than AWAIT_TIME that's fine, the check will simply be performed earlier
            condition.awaitNanos(AWAIT_TIME);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            InterruptedIOException exception = new InterruptedIOException(e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }

    private interface Data {

        char charAt(int index);

        void copyTo(int start, char[] cbuf, int off, int len);
    }

    private static class ArrayData implements Data {

        private char[] array;

        @Override
        public char charAt(int index) {
            return array[index];
        }

        @Override
        public void copyTo(int start, char[] cbuf, int off, int len) {
            System.arraycopy(array, start, cbuf, off, len);
        }
    }

    private static class CharSequenceData implements Data {

        private CharSequence charSequence;

        @Override
        public char charAt(int index) {
            return charSequence.charAt(index);
        }

        @Override
        public void copyTo(int start, char[] cbuf, int off, int len) {
            if (charSequence instanceof String) {
                ((String) charSequence).getChars(start, start + len, cbuf, off);
            } else if (charSequence instanceof StringBuilder) {
                ((StringBuilder) charSequence).getChars(start, start + len, cbuf, off);
            } else if (charSequence instanceof StringBuffer) {
                ((StringBuffer) charSequence).getChars(start, start + len, cbuf, off);
            } else {
                for (int i = start, j = off, k = 0; k < len; i++, j++, k++) {
                    cbuf[j] = charSequence.charAt(i);
                }
            }
        }
    }
}
