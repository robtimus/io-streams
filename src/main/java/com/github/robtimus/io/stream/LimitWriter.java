/*
 * LimitWriter.java
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
import java.io.Writer;
import java.util.Objects;

/**
 * A writer that limits the number of characters that can be written to another writer.
 * <p>
 * If a write call causes the limit to be exceeded and the limit writer is configured to throw an exception, the call will lead to a
 * {@link LimitExceededException} to be thrown.
 *
 * @author Rob Spoor
 */
public final class LimitWriter extends Writer {

    private final Writer writer;

    private final long limit;
    private final LimitExceededStrategy strategy;

    private long position;

    /**
     * Creates a new limit writer. This writer will discard any excess characters.
     *
     * @param writer The writer for which to limit the number of characters that can be written.
     * @param limit The maximum number of characters that can be written.
     * @throws NullPointerException If the given writer is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitWriter(Writer writer, long limit) {
        this(writer, limit, LimitExceededStrategy.DISCARD);
    }

    /**
     * Creates a new limit writer.
     *
     * @param writer The writer for which to limit the number of characters that can be written.
     * @param limit The maximum number of characters that can be written.
     * @param strategy The strategy to follow when the maximum number of characters has exceeded.
     * @throws NullPointerException If the given writer or strategy is {@code null}.
     * @throws IllegalArgumentException If the given limit is negative.
     */
    public LimitWriter(Writer writer, long limit, LimitExceededStrategy strategy) {
        if (limit < 0) {
            throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
        }

        this.writer = Objects.requireNonNull(writer);
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
        writer.write(c);
        position++;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        checkOffsetAndLength(cbuf, off, len);

        if (position >= limit) {
            handleLimitExceeded();
            return;
        }
        int allowedLen = (int) Math.min(limit - position, len);
        writer.write(cbuf, off, allowedLen);
        position += allowedLen;
        if (allowedLen < len) {
            handleLimitExceeded();
        }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        checkOffsetAndLength(str, off, len);

        if (position >= limit) {
            handleLimitExceeded();
            return;
        }
        int allowedLen = (int) Math.min(limit - position, len);
        writer.write(str, off, allowedLen);
        position += allowedLen;
        if (allowedLen < len) {
            handleLimitExceeded();
        }
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        CharSequence cs = csq != null ? csq : "null"; //$NON-NLS-1$
        return append(cs, 0, cs.length());
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        CharSequence cs = csq != null ? csq : "null"; //$NON-NLS-1$
        checkStartAndEnd(cs, start, end);

        if (position >= limit) {
            handleLimitExceeded();
            return this;
        }
        int allowedEnd = (int) Math.min(start + limit - position, end);
        writer.append(cs, start, allowedEnd);
        position += allowedEnd - start;
        if (allowedEnd < end) {
            handleLimitExceeded();
        }
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        if (position >= limit) {
            handleLimitExceeded();
            return this;
        }
        writer.append(c);
        position++;
        return this;
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private void handleLimitExceeded() throws IOException {
        if (strategy.throwException()) {
            throw new LimitExceededException(limit);
        }
    }
}
