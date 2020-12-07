/*
 * LimitInputStream.java
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An input stream that limits the number of bytes that can be read from another input stream.
 * <p>
 * If a read call causes the limit to be exceeded and the limit input stream is configured to throw an exception, the call will lead to a
 * {@link LimitExceededException} to be thrown. Every subsequent read call will return {@code -1}.
 *
 * @author Rob Spoor
 */
public final class LimitInputStream extends InputStream {

    private final InputStream input;

    private final long limit;
    private final LimitExceededStrategy strategy;

    private long position;
    private long mark;

    /**
     * Creates a new limiting input stream. This input stream will discard any excess bytes.
     *
     * @param input The input stream for which to limit the number of bytes that can be read.
     * @param limit The maximum number of bytes that can be read.
     * @throws NullPointerException If the given input stream is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitInputStream(InputStream input, long limit) {
        this(input, limit, LimitExceededStrategy.DISCARD);
    }

    /**
     * Creates a new limiting input stream.
     *
     * @param input The input stream for which to limit the number of bytes that can be read.
     * @param limit The maximum number of bytes that can be read.
     * @param strategy The strategy to follow when the maximum number of bytes has exceeded.
     * @throws NullPointerException If the given input stream or strategy is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitInputStream(InputStream input, long limit, LimitExceededStrategy strategy) {
        if (limit < 0) {
            throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
        }

        this.input = Objects.requireNonNull(input);
        this.limit = limit;
        this.strategy = Objects.requireNonNull(strategy);

        this.position = 0;
        this.mark = 0;
    }

    @Override
    public int read() throws IOException {
        if (position > limit) {
            return -1;
        }
        // if position == limit, allow input.read to return -1 only
        int c = input.read();
        if (c != -1) {
            position++;
            checkLimitExceeded();
        }
        // position == limit means that this is the last byte that is allowed to be read
        return position <= limit ? c : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkOffsetAndLength(b, off, len);

        if (position > limit) {
            return -1;
        }
        // if position == limit, allow input.read to return -1 only
        // one byte must be read though, otherwise this method will forever attempt to read 0 bytes
        int newLen = position == limit ? Math.min(len, 1) : (int) Math.min(limit - position, len);
        int n = input.read(b, off, newLen);
        if (n != -1) {
            position += n;
            checkLimitExceeded();
        }
        // position == limit means that these are the last byte that are allowed to be read
        return position <= limit ? n : -1;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return input.skip(n);
        }
        if (position > limit) {
            return 0;
        }
        // allow skipping one past the limit, so we can detect that it has been exceeded
        long skipped = input.skip(Math.min(limit - position + 1, n));
        position += skipped;
        checkLimitExceeded();
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return position < limit ? (int) Math.min(limit - position, input.available()) : 0;
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }

    @Override
    public synchronized void mark(int readAheadLimit) {
        input.mark(readAheadLimit);
        mark = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        input.reset();
        position = mark;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private void checkLimitExceeded() throws IOException {
        if (position > limit && strategy.throwException()) {
            throw new LimitExceededException(limit);
        }
    }
}
