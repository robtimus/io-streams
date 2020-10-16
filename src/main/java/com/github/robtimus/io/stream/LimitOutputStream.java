/*
 * LimitOutputStream.java
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
import java.io.OutputStream;
import java.util.Objects;

/**
 * An output stream that limits the number of bytes that can be written to another output stream.
 * <p>
 * If a write call causes the limit to be exceeded and the limit output stream is configured to throw an exception, the call will lead to a
 * {@link LimitExceededException} to be thrown.
 *
 * @author Rob Spoor
 */
public class LimitOutputStream extends OutputStream {

    private final OutputStream output;

    private final long limit;
    private final LimitExceededStrategy strategy;

    private long position;

    /**
     * Creates a new limit output stream. This output stream will discard any excess bytes.
     *
     * @param output The output stream for which to limit the number of bytes that can be written.
     * @param limit The maximum number of bytes that can be written.
     * @throws NullPointerException If the given output stream is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitOutputStream(OutputStream output, long limit) {
        this(output, limit, LimitExceededStrategy.DISCARD);
    }

    /**
     * Creates a new limit output stream.
     *
     * @param output The output stream for which to limit the number of bytes that can be written.
     * @param limit The maximum number of bytes that can be written.
     * @param strategy The strategy to follow when the maximum number of bytes has exceeded.
     * @throws NullPointerException If the given output stream or strategy is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitOutputStream(OutputStream output, long limit, LimitExceededStrategy strategy) {
        if (limit < 0) {
            throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
        }

        this.output = Objects.requireNonNull(output);
        this.limit = limit;
        this.strategy = Objects.requireNonNull(strategy);

        this.position = 0;
    }

    @Override
    public void write(int c) throws IOException {
        if (position >= limit) {
            handleLimitExceeded();
            return;
        }
        output.write(c);
        position++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkOffsetAndLength(b, off, len);

        if (position >= limit) {
            handleLimitExceeded();
            return;
        }
        int allowedLen = (int) Math.min(limit - position, len);
        output.write(b, off, allowedLen);
        position += allowedLen;
        if (allowedLen < len) {
            handleLimitExceeded();
        }
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    private void handleLimitExceeded() throws IOException {
        if (strategy.throwException()) {
            throw new LimitExceededException(limit);
        }
    }
}
