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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.input.CloseShieldReader;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.CloseShieldWriter;

/**
 * Utility methods for {@link InputStream InputStreams}, {@link OutputStream OutputStreams}, {@link Reader Readers} and {@link Writer Writers}.
 * Some of these are general purpose methods, others can be used for implementing {@code InputStreams}, {@code OutputStreams}, {@code Readers} and
 * {@code Writers}.
 *
 * @author Rob Spoor
 */
public final class StreamUtils {

    private StreamUtils() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName()); //$NON-NLS-1$
    }

    // wrapping

    /**
     * Wraps an input stream to prevent it from being closed.
     * This method can be used when a method wants to close a passed input stream while the input stream is still needed.
     * <p>
     * This method is much like {@link CloseShieldInputStream}. However, unlike that class, IDEs can still show a warning if the wrapped input stream
     * is not closed elsewhere. This can prevent accidentally forgetting to close input streams completely. For instance:
     * <pre><code>
     * try (InputStream input = new CloseShieldInputStream(new FileInputStream(file))) { // no warning
     * }
     * try (InputStream input = dontClose(new FileInputStream(file))) { // warning
     * }
     * </code></pre>
     *
     * @param input The input stream to wrap.
     * @return An input stream wrapper around the given input stream that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given input stream is {@code null}.
     */
    @SuppressWarnings("resource")
    public static InputStream dontClose(InputStream input) {
        Objects.requireNonNull(input);
        return new CloseShieldInputStream(input);
    }

    /**
     * Wraps an output stream to prevent it from being closed.
     * This method can be used when a method wants to close a passed output stream while the output stream is still needed.
     * Another usage is in a try-with-resources block where a wrapping output stream needs to be closed to finish its work, but the wrapped output
     * stream should still remain open.
     * <p>
     * This method is much like {@link CloseShieldOutputStream}. However, unlike that class, IDEs can still show a warning if the wrapped output
     * stream is not closed elsewhere. This can prevent accidentally forgetting to close output streams completely. For instance:
     * <pre><code>
     * try (OutputStream output = new CloseShieldOutputStream(new FileOutputStream(file))) { // no warning
     * }
     * try (OutputStream output = dontClose(new FileOutputStream(file))) { // warning
     * }
     * </code></pre>
     *
     * @param output The output stream to wrap.
     * @return An output stream wrapper around the given output stream that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given output stream is {@code null}.
     */
    @SuppressWarnings("resource")
    public static OutputStream dontClose(OutputStream output) {
        Objects.requireNonNull(output);
        return new CloseShieldOutputStream(output);
    }

    /**
     * Wraps a reader to prevent it from being closed.
     * This method can be used when a method wants to close a passed reader while the reader is still needed.
     * <p>
     * This method is much like {@link CloseShieldReader}. However, unlike that class, IDEs can still show a warning if the wrapped reader is not
     * closed elsewhere. This can prevent accidentally forgetting to close readers completely. For instance:
     * <pre><code>
     * try (Reader input = new CloseShieldReader(new FileReader(file))) { // no warning
     * }
     * try (Reader input = dontClose(new FileReader(file))) { // warning
     * }
     * </code></pre>
     *
     * @param input The reader to wrap.
     * @return A reader wrapper around the given reader that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given reader is {@code null}.
     */
    @SuppressWarnings("resource")
    public static Reader dontClose(Reader input) {
        Objects.requireNonNull(input);
        return new CloseShieldReader(input);
    }

    /**
     * Wraps a writer to prevent it from being closed.
     * This method can be used when a method wants to close a passed writer while the writer is still needed.
     * Another usage is in a try-with-resources block where a wrapping writer needs to be closed to finish its work, but the wrapped writer should
     * still remain open.
     * <p>
     * This method is much like {@link CloseShieldWriter}. However, unlike that class, IDEs can still show a warning if the wrapped writer is not
     * closed elsewhere. This can prevent accidentally forgetting to close writers completely. For instance:
     * <pre><code>
     * try (Writer output = new CloseShieldWriter(new FileWriter(file))) { // no warning
     * }
     * try (Writer output = dontClose(new FileWriter(file))) { // warning
     * }
     * </code></pre>
     *
     * @param output The writer to wrap.
     * @return A writer wrapper around the given writer that will delegate all methods except for {@code close()}.
     * @throws NullPointerException If the given writer is {@code null}.
     */
    @SuppressWarnings("resource")
    public static Writer dontClose(Writer output) {
        Objects.requireNonNull(output);
        return new CloseShieldWriter(output);
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
