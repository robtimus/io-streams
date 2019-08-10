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
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Objects;
import java.util.function.IntPredicate;

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
     * Returns an {@code InputStream} that wraps a {@code Reader} that contains only ASCII characters. If the {@code Reader} contains any non-ASCII
     * characters, the returned {@code InputStream} will throw an {@link IOException}.
     * <p>
     * This can be used in code that expects an {@code InputStream} where a {@code Reader} is available. One such example is base64 decoding.
     * Even though base64 is text, {@link Decoder} can only wrap {@code InputStream}. This method can help:
     * <pre>InputStream input = Base64.getDecoder().wrap(ascii(reader));</pre>
     * <p>
     * When the returned {@code InputStream} is closed, the given {@code Reader} will be closed as well.
     *
     * @param input The {@code Reader} to wrap.
     * @return An {@code InputStream} that reads from the given {@code Reader} and converts all of its ASCII characters to bytes, and throws an
     *         {@link IOException} for any non-ASCII characters.
     * @throws NullPointerException If the given {@code Reader} is {@code null}.
     */
    public static InputStream ascii(Reader input) {
        Objects.requireNonNull(input);
        return new AsciiInputStream(input);
    }

    /**
     * Returns an {@code OutputStream} that wraps a {@code Writer} to write only ASCII characters. If any non-ASCII bytes are written to the returned
     * {@code OutputStream}, it will thrown an {@link IOException}.
     * <p>
     * This can be used in code that expects an {@code OutputStream} where a {@code Writer} is available. One such example is base64 encoding.
     * Even though base64 is text, {@link Encoder} can only wrap {@code OutputStream}. This method can help:
     * <pre>OutputStream output = Base64.getEncoder().wrap(ascii(writer));</pre>
     * <p>
     * When the returned {@code OutputStream} is closed, the given {@code Writer} will be closed as well.
     *
     * @param output The {@code Writer} to wrap.
     * @return An {@code OutputStream} that writes all ASCII bytes written to it to the given {@code Writer}, and throws an {@link IOException} for
     *         any non-ASCII bytes.
     * @throws NullPointerException If the given {@code Writer} is {@code null}.
     */
    public static OutputStream ascii(Writer output) {
        Objects.requireNonNull(output);
        return new AsciiOutputStream(output);
    }

    /**
     * Returns an {@code InputStream} that filters the contents of another {@code InputStream}.
     * For instance, the following can be used to create an {@code InputStream} that does not return any whitespace characters:
     * <pre>InputStream filtering = filtering(input, Character::isWhitespace);</pre>
     * <p>
     * When the returned {@code InputStream} is closed, the given {@code InputStream} will be closed as well.
     *
     * @param input The {@code InputStream} to filter.
     * @param filter The predicate to use to filter out bytes.
     *                   Any byte for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @return An {@code InputStream} that filters the contents of the given {@code InputStream} using the given predicate.
     * @throws NullPointerException If the given {@code InputStream} or predicate is {@code null}.
     */
    public static InputStream filtering(InputStream input, IntPredicate filter) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(filter);
        return new FilteringInputStream(input, filter);
    }

    /**
     * Returns an {@code OutputStream} that filters the contents of another {@code OutputStream}.
     * For instance, the following can be used to create an {@code OutputStream} that does not write any whitespace characters:
     * <pre>OutputStream filtering = filtering(output, Character::isWhitespace);</pre>
     * <p>
     * When the returned {@code OutputStream} is closed, the given {@code OutputStream} will be closed as well.
     *
     * @param output The {@code OutputStream} to filter.
     * @param filter The predicate to use to filter out bytes.
     *                   Any byte for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @return An {@code OutputStream} that filters contents using the given predicate before writing to the given {@code OutputStream}.
     * @throws NullPointerException If the given {@code OutputStream} or predicate is {@code null}.
     */
    public static OutputStream filtering(OutputStream output, IntPredicate filter) {
        Objects.requireNonNull(output);
        Objects.requireNonNull(filter);
        return new FilteringOutputStream(output, filter);
    }

    /**
     * Returns a {@code Reader} that filters the contents of another {@code Reader}.
     * For instance, the following can be used to create an {@code Reader} that does not return any whitespace characters:
     * <pre>Reader filtering = filtering(input, Character::isWhitespace);</pre>
     * <p>
     * When the returned {@code Reader} is closed, the given {@code Reader} will be closed as well.
     *
     * @param input The {@code Reader} to filter.
     * @param filter The predicate to use to filter out characters.
     *                   Any character for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @return A {@code Reader} that filters the contents of the given {@code Reader} using the given predicate.
     * @throws NullPointerException If the given {@code Reader} or predicate is {@code null}.
     */
    public static Reader filtering(Reader input, IntPredicate filter) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(filter);
        return new FilteringReader(input, filter);
    }

    /**
     * Returns a {@code Writer} that filters the contents of another {@code Writer}.
     * For instance, the following can be used to create a {@code Writer} that does not write any whitespace characters:
     * <pre>Writer filtering = filtering(output, Character::isWhitespace);</pre>
     * <p>
     * When the returned {@code Writer} is closed, the given {@code Writer} will be closed as well.
     *
     * @param output The {@code Writer} to filter.
     * @param filter The predicate to use to filter out bytes.
     *                   Any byte for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @return A {@code Writer} that filters contents using the given predicate before writing to the given {@code Writer}.
     * @throws NullPointerException If the given {@code Writer} or predicate is {@code null}.
     */
    public static Writer filtering(Writer output, IntPredicate filter) {
        Objects.requireNonNull(output);
        Objects.requireNonNull(filter);
        return new FilteringWriter(output, filter);
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
        return new DontCloseInputStream(input);
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
        return new DontCloseOutputStream(output);
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
        return new DontCloseReader(input);
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
     * Checks whether or not an offset and length are valid for a {@code CharSequence}.
     * This method can be used for checking input for {@link Writer#write(String, int, int)}.
     *
     * @param sequence The {@code CharSequence} to check for.
     * @param offset The offset to check, inclusive.
     * @param length The length to check.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     * @throws IndexOutOfBoundsException If the given offset is negative, the given length is negative,
     *                                       or the given offset and length exceed the given {@code CharSequence}'s length.
     */
    public static void checkOffsetAndLength(CharSequence sequence, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > sequence.length()) {
            throw new ArrayIndexOutOfBoundsException(Messages.charSequence.invalidOffsetOrLength.get(sequence.length(), offset, length));
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
