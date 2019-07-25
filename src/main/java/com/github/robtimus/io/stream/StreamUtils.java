/*
 * StreamUtils.java
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Objects;

/**
 * Utility methods for {@link InputStream InputStreams}, {@link OutputStream OutputStreams}, {@link Reader Readers} and {@link Writer Writers}.
 * Some of these are general purpose methods, others can be used for implementing {@code InputStreams}, {@code OutputStreams}, {@code Readers} and
 * {@code Writers}.
 *
 * @author Rob Spoor
 */
public final class StreamUtils {

    private StreamUtils() {
        throw new Error("cannot create instances of " + getClass().getName()); //$NON-NLS-1$
    }

    // wrapping

    /**
     * Returns an {@code Appendable} as a {@code Writer}. If the given {@code Appendable} is a {@code Writer}, it is returned unmodified.
     * Otherwise, a wrapper is returned that will delegate all calls to the wrapped {@code Appendable}. This includes {@link Writer#flush() flush()}
     * if the wrapped {@code Appendable} implements {@link Flushable}, and {@link Writer#close() close()} if the wrapped {@code Appendable} implements
     * {@link Closeable} or {@link AutoCloseable}.
     * <p>
     * Note that the behaviour of closing a {@code Writer} wrapper depends on the wrapped {@code Appendable}. If it does not support closing,
     * or if it still allows text to be appended after closing, then the closed {@code AppendableWriter} allows text to be appended after closing.
     * If it does not allow text to be appended after closing, then neither will the closed {@code Writer} wrapper.
     *
     * @param appendable The {@code Appendable} to return a {@code Writer} for.
     * @return The given {@code Appendable} itself if it's already a {@code Writer}, otherwise a wrapper around the given {@code Appendable}.
     * @throws NullPointerException If the given {@code Appendable} is {@code null}.
     */
    public static Writer writer(Appendable appendable) {
        Objects.requireNonNull(appendable);
        return appendable instanceof Writer ? (Writer) appendable : new AppendableWriter(appendable);
    }

    /**
     * Wraps an {@code InputStream} to prevent it from being closed.
     * This method can be used when a method wants to close a passed {@code InputStream} while the {@code InputStream} is still needed.
     * <p>
     * The returned {@code InputStream} delegates all methods except for {@link InputStream#close() close()} to the given {@code InputStream}.
     *
     * @param input The {@code InputStream} to wrap.
     * @return An {@code InputStream} wrapper around the given {@code InputStream} that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given {@code InputStream} is {@code null}.
     */
    public static InputStream dontClose(InputStream input) {
        Objects.requireNonNull(input);
        return new InputStream() {

            private boolean closed = false;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw streamClosedException();
                }
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return input.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                ensureOpen();
                return input.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                ensureOpen();
                return input.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                ensureOpen();
                return input.skip(n);
            }

            @Override
            public int available() throws IOException {
                ensureOpen();
                return input.available();
            }

            @Override
            public void close() throws IOException {
                // don't close input
                closed = true;
            }

            @Override
            public synchronized void mark(int readlimit) {
                if (!closed) {
                    input.mark(readlimit);
                }
            }

            @Override
            public synchronized void reset() throws IOException {
                ensureOpen();
                input.reset();
            }

            @Override
            public boolean markSupported() {
                return input.markSupported();
            }

            @Override
            @SuppressWarnings("nls")
            public String toString() {
                return StreamUtils.class.getName() + "#dontClose(" + input + ")";
            }
        };
    }

    /**
     * Wraps an {@code OutputStream} to prevent it from being closed.
     * This method can be used when a method wants to close a passed {@code OutputStream} while the {@code OutputStream} is still needed.
     * Another usage is in a try-with-resources block where a wrapping {@code OutputStream} needs to be closed to finish its work, but the wrapped
     * {@code OutputStream} should still remain open.
     * <p>
     * The returned {@code OutputStream} delegates all methods except for {@link OutputStream#close() close()} to the given {@code OutputStream}.
     *
     * @param output The {@code OutputStream} to wrap.
     * @return An {@code OutputStream} wrapper around the given {@code OutputStream} that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given {@code OutputStream} is {@code null}.
     */
    public static OutputStream dontClose(OutputStream output) {
        Objects.requireNonNull(output);
        return new OutputStream() {

            private boolean closed = false;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw streamClosedException();
                }
            }

            @Override
            public void write(int b) throws IOException {
                ensureOpen();
                output.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                ensureOpen();
                output.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ensureOpen();
                output.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                ensureOpen();
                output.flush();
            }

            @Override
            public void close() throws IOException {
                // don't close output
                closed = true;
            }

            @Override
            @SuppressWarnings("nls")
            public String toString() {
                return StreamUtils.class.getName() + "#dontClose(" + output + ")";
            }
        };
    }

    /**
     * Wraps a {@code Reader} to prevent it from being closed.
     * This method can be used when a method wants to close a passed {@code Reader} while the {@code Reader} is still needed.
     * <p>
     * The returned {@code Reader} delegates all methods except for {@link Reader#close() close()} to the given {@code Reader}.
     *
     * @param input The {@code Reader} to wrap.
     * @return A {@code Reader} wrapper around the given {@code Reader} that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given {@code Reader} is {@code null}.
     */
    public static Reader dontClose(Reader input) {
        Objects.requireNonNull(input);
        return new Reader(input) {

            private boolean closed = false;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw streamClosedException();
                }
            }

            @Override
            public int read(CharBuffer target) throws IOException {
                ensureOpen();
                return input.read(target);
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return input.read();
            }

            @Override
            public int read(char[] cbuf) throws IOException {
                ensureOpen();
                return input.read(cbuf);
            }

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                ensureOpen();
                return input.read(cbuf, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                ensureOpen();
                return input.skip(n);
            }

            @Override
            public boolean ready() throws IOException {
                ensureOpen();
                return input.ready();
            }

            @Override
            public boolean markSupported() {
                return input.markSupported();
            }

            @Override
            public void mark(int readAheadLimit) throws IOException {
                ensureOpen();
                input.mark(readAheadLimit);
            }

            @Override
            public void reset() throws IOException {
                ensureOpen();
                input.reset();
            }

            @Override
            public void close() throws IOException {
                // don't close input
                closed = true;
            }

            @Override
            @SuppressWarnings("nls")
            public String toString() {
                return StreamUtils.class.getName() + "#dontClose(" + input + ")";
            }
        };
    }

    /**
     * Wraps a {@code Writer} to prevent it from being closed.
     * This method can be used when a method wants to close a passed {@code Writer} while the {@code Writer} is still needed.
     * Another usage is in a try-with-resources block where a wrapping {@code Writer} needs to be closed to finish its work, but the wrapped
     * {@code Writer} should still remain open.
     * <p>
     * The returned {@code Writer} delegates all methods except for {@link Writer#close() close()} to the given {@code Writer}.
     *
     * @param output The {@code Writer} to wrap.
     * @return A {@code Writer} wrapper around the given {@code Writer} that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given {@code Writer} is {@code null}.
     */
    public static Writer dontClose(Writer output) {
        Objects.requireNonNull(output);
        return new Writer(output) {

            private boolean closed = false;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw streamClosedException();
                }
            }

            @Override
            public void write(int c) throws IOException {
                ensureOpen();
                output.write(c);
            }

            @Override
            public void write(char[] cbuf) throws IOException {
                ensureOpen();
                output.write(cbuf);
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                ensureOpen();
                output.write(cbuf, off, len);
            }

            @Override
            public void write(String str) throws IOException {
                ensureOpen();
                output.write(str);
            }

            @Override
            public void write(String str, int off, int len) throws IOException {
                ensureOpen();
                output.write(str, off, len);
            }

            @Override
            public Writer append(CharSequence csq) throws IOException {
                ensureOpen();
                output.append(csq);
                return this;
            }

            @Override
            public Writer append(CharSequence csq, int start, int end) throws IOException {
                ensureOpen();
                output.append(csq, start, end);
                return this;
            }

            @Override
            public Writer append(char c) throws IOException {
                ensureOpen();
                output.append(c);
                return this;
            }

            @Override
            public void flush() throws IOException {
                ensureOpen();
                output.flush();
            }

            @Override
            public void close() throws IOException {
                // don't close output
                closed = true;
            }

            @Override
            @SuppressWarnings("nls")
            public String toString() {
                return StreamUtils.class.getName() + "#dontClose(" + output + ")";
            }
        };
    }

    // index checking

    /**
     * Checks whether or not an offset and length are valid for a byte array.
     * This method can be used for checking input for {@link InputStream#read(byte[], int, int)} or {@link OutputStream#write(byte[], int, int)}.
     *
     * @param array The array to check for.
     * @param offset The offset to check, inclusive.
     * @param length The length to check.
     * @throws NullPointerException If the given array is {@code null}.
     * @throws IndexOutOfBoundsException If the given offset is negative, the given length is negative,
     *                                       or the given offset and length exceed the given array's length.
     */
    public static void checkOffsetAndLength(byte[] array, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidOffsetOrLength.get(array.length, offset, length));
        }
    }

    static void checkStartAndEnd(byte[] array, int start, int end) {
        if (start < 0 || end > array.length || start > end) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidStartOrEnd.get(array.length, start, end));
        }
    }

    /**
     * Checks whether or not an offset and length are valid for a character array.
     * This method can be used for checking input for {@link Reader#read(char[], int, int)} or {@link Writer#write(char[], int, int)}.
     *
     * @param array The array to check for.
     * @param offset The offset to check, inclusive.
     * @param length The length to check.
     * @throws NullPointerException If the given array is {@code null}.
     * @throws IndexOutOfBoundsException If the given offset is negative, the given length is negative,
     *                                       or the given offset and length exceed the given array's length.
     */
    public static void checkOffsetAndLength(char[] array, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidOffsetOrLength.get(array.length, offset, length));
        }
    }

    static void checkStartAndEnd(char[] array, int start, int end) {
        if (start < 0 || end > array.length || start > end) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidStartOrEnd.get(array.length, start, end));
        }
    }

    /**
     * Checks whether or not a start and end index are valid for a {@code CharSequence}.
     * This method can be used for checking input for {@link Writer#append(CharSequence, int, int)}.
     *
     * @param sequence The {@code CharSequence} to check for.
     * @param start The start index to check, inclusive.
     * @param end The end index to check, exclusive.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given {@code CharSequence}'s length,
     *                                       or the given start index is larger than the given end index.
     */
    public static void checkStartAndEnd(CharSequence sequence, int start, int end) {
        if (start < 0 || end > sequence.length() || start > end) {
            throw new ArrayIndexOutOfBoundsException(Messages.array.invalidStartOrEnd.get(sequence.length(), start, end));
        }
    }

    // exceptions

    /**
     * Returns an {@code IOException} that indicates a stream is closed.
     *
     * @return An {@code IOException} that indicates a stream is closed.
     */
    public static IOException streamClosedException() {
        return new IOException(Messages.stream.closed.get());
    }
}
