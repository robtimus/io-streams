/*
 * StreamUtils.java
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
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
     * Returns a reader wrapper around a character sequence. This is a utility method that delegates to
     * {@link #reader(CharSequence, int, int) reader(sequence, 0, sequence.length())}.
     *
     * @param sequence The character sequence to return a reader for.
     * @return A reader wrapper around the given character sequence.
     * @throws NullPointerException If the given character sequence is {@code null}.
     */
    public static Reader reader(CharSequence sequence) {
        return reader(sequence, 0, sequence.length());
    }

    /**
     * Returns a reader wrapper around a portion of a character sequence.
     * This reader is much like {@link StringReader}, except it supports any character sequence as well as sub sequences.
     * Like {@code StringReader} it supports {@link Reader#mark(int)} and {@link Reader#reset()}. Unlike {@code StringReader}, it's not thread safe.
     * <p>
     * After the returned reader has been closed, attempting to read from it will result in an {@link IOException}.
     *
     * @param sequence The character sequence to return a reader for.
     * @param start The index to start reading at, inclusive.
     * @param end The index to stop reading at, exclusive.
     * @return A reader wrapper around the given portion of the given character sequence.
     * @throws NullPointerException If the given character sequence is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given character sequence's length,
     *                                       or the given start index is larger than the given end index.
     */
    public static Reader reader(CharSequence sequence, int start, int end) {
        checkStartAndEnd(sequence, start, end);
        return new CharSequenceReader(sequence, start, end);
    }

    /**
     * Returns an appendable as a writer.
     * If the given appendable is a writer, it is returned unmodified. Otherwise, a wrapper is returned that will delegate all calls to the wrapped
     * appendable. This includes {@link Writer#flush() flush()} if the wrapped appendable implements {@link Flushable},
     * and {@link Writer#close() close()} if the wrapped appendable implements {@link Closeable} or {@link AutoCloseable}.
     * <p>
     * Note that the behaviour of closing a writer wrapper depends on the wrapped appendable. If it does not support closing, or if it still allows
     * text to be appended after closing, then the closed writer allows text to be appended after closing. If it does not allow text to be appended
     * after closing, then neither will the closed writer wrapper.
     *
     * @param appendable The appendable to return a writer for.
     * @return The given appendable itself if it's already a writer, otherwise a wrapper around the given appendable.
     * @throws NullPointerException If the given appendable is {@code null}.
     */
    public static Writer writer(Appendable appendable) {
        Objects.requireNonNull(appendable);
        return appendable instanceof Writer ? (Writer) appendable : new AppendableWriter(appendable);
    }

    /**
     * Wraps an input stream to prevent it from being closed.
     * This method can be used when a method wants to close a passed input stream while the input stream is still needed.
     * <p>
     * The returned input stream delegates all methods except for {@link InputStream#close() close()} to the given input stream.
     *
     * @param input The input stream to wrap.
     * @return An input stream wrapper around the given input stream that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given input stream is {@code null}.
     */
    public static InputStream dontClose(InputStream input) {
        return new DontCloseInputStream(input);
    }

    /**
     * Wraps an output stream to prevent it from being closed.
     * This method can be used when a method wants to close a passed output stream while the output stream is still needed.
     * Another usage is in a try-with-resources block where a wrapping output stream needs to be closed to finish its work, but the wrapped output
     * stream should still remain open.
     * <p>
     * The returned output stream delegates all methods except for {@link OutputStream#close() close()} to the given output stream.
     *
     * @param output The output stream to wrap.
     * @return An output stream wrapper around the given output stream that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given output stream is {@code null}.
     */
    public static OutputStream dontClose(OutputStream output) {
        return new DontCloseOutputStream(output);
    }

    /**
     * Wraps a reader to prevent it from being closed.
     * This method can be used when a method wants to close a passed reader while the reader is still needed.
     * <p>
     * The returned reader delegates all methods except for {@link Reader#close() close()} to the given reader.
     *
     * @param input The reader to wrap.
     * @return A reader wrapper around the given reader that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given reader is {@code null}.
     */
    public static Reader dontClose(Reader input) {
        return new DontCloseReader(input);
    }

    /**
     * Wraps a writer to prevent it from being closed.
     * This method can be used when a method wants to close a passed writer while the writer is still needed.
     * Another usage is in a try-with-resources block where a wrapping writer needs to be closed to finish its work, but the wrapped writer should
     * still remain open.
     * <p>
     * The returned writer delegates all methods except for {@link Writer#close() close()} to the given writer.
     *
     * @param output The writer to wrap.
     * @return A writer wrapper around the given writer that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given writer is {@code null}.
     */
    public static Writer dontClose(Writer output) {
        return new DontCloseWriter(output);
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
     * Checks whether or not an offset and length are valid for a character sequence.
     * This method can be used for checking input for {@link Writer#write(String, int, int)}.
     *
     * @param sequence The character sequence to check for.
     * @param offset The offset to check, inclusive.
     * @param length The length to check.
     * @throws NullPointerException If the given character sequence is {@code null}.
     * @throws IndexOutOfBoundsException If the given offset is negative, the given length is negative,
     *                                       or the given offset and length exceed the given character sequence's length.
     */
    public static void checkOffsetAndLength(CharSequence sequence, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > sequence.length()) {
            throw new ArrayIndexOutOfBoundsException(Messages.charSequence.invalidOffsetOrLength.get(sequence.length(), offset, length));
        }
    }

    /**
     * Checks whether or not a start and end index are valid for a character sequence.
     * This method can be used for checking input for {@link Writer#append(CharSequence, int, int)}.
     *
     * @param sequence The character sequence to check for.
     * @param start The start index to check, inclusive.
     * @param end The end index to check, exclusive.
     * @throws NullPointerException If the given character sequence is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given character sequence's length,
     *                                       or the given start index is larger than the given end index.
     */
    public static void checkStartAndEnd(CharSequence sequence, int start, int end) {
        if (start < 0 || end > sequence.length() || start > end) {
            throw new ArrayIndexOutOfBoundsException(Messages.charSequence.invalidStartOrEnd.get(sequence.length(), start, end));
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
