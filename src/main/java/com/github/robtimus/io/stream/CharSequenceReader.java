/*
 * CharSequenceReader.java
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
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * A {@link Reader} implementation backed by a {@link CharSequence}. This is much like {@link StringReader}, except it supports any
 * {@code CharSequence} as well as sub sequences. The latter distinguishes it from
 * <a href="https://commons.apache.org/proper/commons-io/javadocs/api-2.6/org/apache/commons/io/input/CharSequenceReader.html">Common IO's
 * CharSequenceReader</a>, which requires calling {@link CharSequence#subSequence(int, int)} if you want to read only a portion of a
 * {@code CharSequence}. In addition, trying to read from a closed {@code CharSequenceReader} will cause exceptions to be thrown, much like
 * {@code StringReader} does.
 * <p>
 * Like {@code StringReader} it supports {@link #mark(int)} and {@link #reset()}.
 *
 * @author Rob Spoor
 */
public final class CharSequenceReader extends Reader {

    private CharSequence source;
    private final int end;

    private int index;
    private int mark;

    /**
     * Creates a new reader that will read from a {@code CharSequence}.
     *
     * @param source The {@code CharSequence} to read from.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     */
    public CharSequenceReader(CharSequence source) {
        this(source, 0, source.length());
    }

    /**
     * Creates a new reader that will read from a portion of a {@code CharSequence}.
     *
     * @param source The {@code CharSequence} to read from.
     * @param start The index to start reading at, inclusive.
     * @param end The index to stop reading at, exclusive.
     * @throws NullPointerException If the given {@code CharSequence} is {@code null}.
     * @throws IndexOutOfBoundsException If the given start index is negative,
     *                                       the given end index is larger than the given {@code CharSequence}'s length,
     *                                       or the given start index is larger than the given end index.
     */
    public CharSequenceReader(CharSequence source, int start, int end) {
        checkStartAndEnd(source, start, end);
        this.source = source;
        this.end = end;

        index = start;
        mark = index;
    }

    private void checkClosed() throws IOException {
        if (source == null) {
            throw streamClosedException();
        }
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        return index < end ? source.charAt(index++) : -1;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        checkClosed();
        checkOffsetAndLength(cbuf, off, len);
        if (len == 0) {
            return 0;
        }
        if (index >= end) {
            return -1;
        }
        int read = Math.min(len, end - index);
        if (source instanceof String) {
            String s = (String) source;
            s.getChars(index, index + read, cbuf, off);
            index += read;
        } else {
            for (int i = 0, j = off; i < len && index < end; i++, j++, index++) {
                cbuf[j] = source.charAt(index);
            }
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        checkClosed();
        if (n < 0) {
            throw new IllegalArgumentException(n + " < 0"); //$NON-NLS-1$
        }
        if (n == 0 || index >= end) {
            return 0;
        }
        int newIndex = (int) Math.min(end, index + Math.min(n, Integer.MAX_VALUE));
        long skipped = newIndex - index;
        index = newIndex;
        return skipped;
    }

    @Override
    public boolean ready() throws IOException {
        checkClosed();
        return index < end;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        checkClosed();
        mark = index;
    }

    @Override
    public void reset() throws IOException {
        checkClosed();
        index = mark;
    }

    @Override
    public void close() throws IOException {
        source = null;
    }
}
