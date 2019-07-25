/*
 * HexOutputStream.java
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
import static com.github.robtimus.io.stream.StreamUtils.streamClosedException;
import static com.github.robtimus.io.stream.StreamUtils.writer;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * An output stream that converts bytes into hex.
 * <p>
 * When a hex output stream is closed, its wrapped {@link Appendable} will also be closed if it implements {@link Closeable} or {@link AutoCloseable}.
 *
 * @author Rob Spoor
 */
public final class HexOutputStream extends OutputStream {

    private static final int WRITE_BUFFER_SIZE = 1024;

    private Writer destination;

    private final boolean upperCase;

    private char[] writeBuffer;

    /**
     * Creates a new hex output stream. It will write hex in lower case.
     *
     * @param destination The {@code Appendable} to write to.
     * @throws NullPointerException If the given {@code Appendable} is {@code null}.
     */
    public HexOutputStream(Appendable destination) {
        this(destination, false);
    }

    /**
     * Creates a new hex output stream.
     *
     * @param destination The {@code Appendable} to write to.
     * @param upperCase {@code true} to write hex in upper case, or {@code false} to write hex in lower case.
     * @throws NullPointerException If the given {@code Appendable} is {@code null}.
     */
    public HexOutputStream(Appendable destination, boolean upperCase) {
        this.destination = writer(destination);
        this.upperCase = upperCase;
    }

    private void ensureOpen() throws IOException {
        if (destination == null) {
            throw streamClosedException();
        }
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        if (writeBuffer == null){
            writeBuffer = new char[WRITE_BUFFER_SIZE];
        }
        writeBuffer[0] = high(b, upperCase);
        writeBuffer[1] = low(b, upperCase);
        destination.write(writeBuffer, 0, 2);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        checkOffsetAndLength(b, off, len);

        char cbuf[];
        int lenInHex = len * 2;
        if (lenInHex <= WRITE_BUFFER_SIZE) {
            if (writeBuffer == null) {
                writeBuffer = new char[WRITE_BUFFER_SIZE];
            }
            cbuf = writeBuffer;
        } else {
            // Don't permanently allocate very large buffers.
            cbuf = new char[lenInHex];
        }
        for (int i = off, j = 0, k = 0; j < len; i++, j++, k += 2) {
            cbuf[k] = high(b[i], upperCase);
            cbuf[k + 1] = low(b[i], upperCase);
        }
        destination.write(cbuf, 0, lenInHex);
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        destination.flush();
    }

    @Override
    public void close() throws IOException {
        if (destination != null) {
            destination.close();
            destination = null;
        }
    }

    /**
     * Converts a byte array to its lower case hex representation.
     *
     * @param b The byte array to convert.
     * @return The hex representation of the given byte array.
     * @throws NullPointerException If the given byte array is {@code null}.
     */
    public static String toHex(byte[] b) {
        return toHex(b, 0, b.length);
    }

    /**
     * Converts a portion of byte array to its lower case hex representation.
     *
     * @param b The byte array to convert a portion of.
     * @param start The start index of the portion to convert, inclusive.
     * @param end The end index of the portion to convert, exclusive.
     * @return The hex representation of the given portion of the given byte array.
     * @throws NullPointerException If the given byte array is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative, the given end index is larger than the given array's length,
     *                                       or the given start index is larger than the given end index.
     */
    public static String toHex(byte[] b, int start, int end) {
        return toHex(b, start, end, false);
    }

    /**
     * Converts a byte array to its hex representation.
     *
     * @param b The byte array to convert.
     * @param upperCase {@code true} to return hex in upper case, or {@code false} to return hex in lower case.
     * @return The hex representation of the given byte array.
     * @throws NullPointerException If the given byte array is {@code null}.
     */
    public static String toHex(byte[] b, boolean upperCase) {
        return toHex(b, 0, b.length, upperCase);
    }

    /**
     * Converts a portion of a byte array to its hex representation.
     *
     * @param b The byte array to convert a portion of.
     * @param start The start index of the portion to convert, inclusive.
     * @param end The end index of the portion to convert, exclusive.
     * @param upperCase {@code true} to return hex in upper case, or {@code false} to return hex in lower case.
     * @return The hex representation of the given portion of the given byte array.
     * @throws NullPointerException If the given byte array is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative, the given end index is larger than the given array's length,
     *                                       or the given start index is larger than the given end index.
     */
    public static String toHex(byte[] b, int start, int end, boolean upperCase) {
        checkStartAndEnd(b, start, end);
        char[] c = new char[(end - start) * 2];
        for (int i = start, j = 0; i < end; i++, j += 2) {
            c[j] = high(b[i], upperCase);
            c[j + 1] = low(b[i], upperCase);
        }
        return new String(c);
    }

    private static char high(int b, boolean upperCase) {
        int high = (b >> 4) & 0xF;
        char c = Character.forDigit(high, 16);
        return upperCase ? Character.toUpperCase(c) : Character.toLowerCase(c);
    }

    private static char low(int b, boolean upperCase) {
        int low = b & 0xF;
        char c = Character.forDigit(low, 16);
        return upperCase ? Character.toUpperCase(c) : Character.toLowerCase(c);
    }
}
