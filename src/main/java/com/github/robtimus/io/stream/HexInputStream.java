/*
 * HexInputStream.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import static com.github.robtimus.io.stream.StreamUtils.streamClosedException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;

/**
 * An input stream that hex into bytes.
 * <p>
 * When a hex input stream is closed, its source will also be closed.
 *
 * @author Rob Spoor
 */
public final class HexInputStream extends InputStream {

    private static final int READ_BUFFER_SIZE = 1024;

    private Reader source;

    private char[] readBuffer;

    private IOException exception;

    /**
     * Creates a new hex input stream.
     *
     * @param source The source of the hex characters.
     * @throws NullPointerException If the given source is {@code null}.
     */
    public HexInputStream(Reader source) {
        this.source = Objects.requireNonNull(source);
    }

    private void ensureOpen() throws IOException {
        if (source == null) {
            throw streamClosedException();
        }
    }

    private void checkException() throws IOException {
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        checkException();

        if (readBuffer == null) {
            readBuffer = new char[READ_BUFFER_SIZE];
        }
        int read = read(readBuffer, 2);
        if (read == -1) {
            return -1;
        }
        int high = convert(readBuffer[0]);
        int low = convert(readBuffer[1]);
        return combine(high, low) & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        checkException();

        char cbuf[];
        int lenInHex = len * 2;
        if (lenInHex <= READ_BUFFER_SIZE) {
            if (readBuffer == null) {
                readBuffer = new char[READ_BUFFER_SIZE];
            }
            cbuf = readBuffer;
        } else {
            // Don't permanently allocate very large buffers.
            cbuf = new char[lenInHex];
        }

        int read = read(cbuf, lenInHex);
        if (read == -1) {
            return -1;
        }

        for (int i = off, j = 0; j < read; i++, j += 2) {
            int high = convert(cbuf[j]);
            int low = convert(cbuf[j + 1]);
            b[i] = combine(high, low);
        }
        return read / 2;
    }

    private int read(char[] cbuf, int lenInHex) throws IOException {

        int read = source.read(cbuf, 0, lenInHex);
        if (read == -1) {
            return -1;
        }
        while ((read & 1) == 1) {
            int n = source.read(cbuf, read, lenInHex - read);
            if (n == -1) {
                // no more data and an odd number of characters - premature EOF
                exception = new EOFException(Messages.hex.eof.get());
                throw exception;
            }
            read += n;
        }
        return read;
    }

    private int convert(char c) throws IOException {
        int value = Character.digit(c, 16);
        if (value == -1) {
            exception = new IOException(Messages.hex.invalidChar.get(c));
            throw exception;
        }
        return value;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return 0;
    }

    @Override
    public void close() throws IOException {
        if (source != null) {
            source.close();
            source = null;
        }
    }

    /**
     * Converts a hex representation into a byte array.
     *
     * @param hex The {@code CharSequence} with the hex representation to convert.
     * @return The bytes from the given hex representation.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     * @throws IllegalArgumentException If the given {@code CharSequence} does not contain a valid hex representation.
     */
    public static byte[] fromHex(CharSequence hex) {
        return fromHex(hex, 0, hex.length());
    }

    /**
     * Converts a hex representation into a byte array.
     *
     * @param hex The {@code CharSequence} with the hex representation to convert.
     * @param start The start index of the {@code CharSequence} of the hex representation to convert, inclusive.
     * @param end The end index of the {@code CharSequence} of the hex representation to convert, exclusive.
     * @return The bytes from the given hex representation.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given {@code CharSequence}'s length,
     *                                       or the given start index is larger than the given end index.
     * @throws IllegalArgumentException If the given {@code CharSequence} does not contain a valid hex representation.
     */
    public static byte[] fromHex(CharSequence hex, int start, int end) {
        checkStartAndEnd(hex, start, end);
        int length = end - start;
        if ((length & 1) == 1) {
            throw new IllegalArgumentException(Messages.hex.eof.get());
        }
        byte[] b = new byte[length / 2];
        for (int i = 0, j = start; j < end; i++, j += 2) {
            int high = convert(hex, j);
            int low = convert(hex, j + 1);
            b[i] = combine(high, low);
        }
        return b;
    }

    private static int convert(CharSequence hex, int index) {
        char c = hex.charAt(index);
        int value = Character.digit(c, 16);
        if (value == -1) {
            throw new IllegalArgumentException(Messages.hex.invalidChar.get(c));
        }
        return value;
    }

    /**
     * Attempts to converts a hex representation into a byte array.
     *
     * @param hex The {@code CharSequence} with the hex representation to convert.
     * @return An {@link Optional} with the bytes from the given hex representation,
     *         or {@link Optional#empty()} if the {@code CharSequence} is {@code null} or does not contain a valid hex representation.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given {@code CharSequence}'s length,
     *                                       or the given start index is larger than the given end index.
     */
    public static Optional<byte[]> tryFromHex(CharSequence hex) {
        return hex == null ? Optional.empty() : tryFromHex(hex, 0, hex.length());
    }

    /**
     * Attempts to converts a hex representation into a byte array.
     *
     * @param hex The {@code CharSequence} with the hex representation to convert.
     * @param start The start index of the {@code CharSequence} of the hex representation to convert, inclusive.
     * @param end The end index of the {@code CharSequence} of the hex representation to convert, exclusive.
     * @return An {@link Optional} with the bytes from the given hex representation,
     *         or {@link Optional#empty()} if the {@code CharSequence} is {@code null} or does not contain a valid hex representation.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given {@code CharSequence}'s length,
     *                                       or the given start index is larger than the given end index.
     */
    public static Optional<byte[]> tryFromHex(CharSequence hex, int start, int end) {
        if (hex == null) {
            return Optional.empty();
        }
        checkStartAndEnd(hex, start, end);
        int length = end - start;
        if ((length & 1) == 1) {
            return Optional.empty();
        }
        byte[] b = new byte[length / 2];
        for (int i = 0, j = start; j < end; i++, j += 2) {
            int high = tryConvert(hex, j);
            int low = tryConvert(hex, j + 1);
            if (high == -1 || low == -1) {
                return Optional.empty();
            }
            b[i] = combine(high, low);
        }
        return Optional.of(b);
    }

    private static int tryConvert(CharSequence hex, int index) {
        char c = hex.charAt(index);
        return Character.digit(c, 16);
    }

    private static byte combine(int high, int low) {
        return (byte) ((high << 4 | low) & 0xFF);
    }
}
