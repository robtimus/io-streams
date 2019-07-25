/*
 * FilteringWriter.java
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
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * A writer that filters the contents for another writer.
 * For instance, to create a writer that does not write any whitespace characters, call {@code new FilteringWriter(output, Character::isWhitespace)}.
 * <p>
 * When a filtering writer is closed, the wrapped writer will also be closed.
 *
 * @author Rob Spoor
 */
public final class FilteringWriter extends Writer {

    private static final int WRITE_BUFFER_SIZE = 1024;

    private final Writer output;
    private final IntPredicate filter;

    private char[] writeBuffer;

    /**
     * Creates a new filtering writer.
     *
     * @param output The {@code Writer} to wrap.
     * @param filter The predicate to use to filter out bytes.
     *                   Any byte for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @throws NullPointerException If the given {@code Writer} or predicate is {@code null}.
     */
    public FilteringWriter(Writer output, IntPredicate filter) {
        this.output = Objects.requireNonNull(output);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public void write(int c) throws IOException {
        if (!filter.test((char) c)) {
            output.write(c);
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        checkOffsetAndLength(cbuf, off, len);

        char[] buffer;
        if (len <= WRITE_BUFFER_SIZE) {
            if (writeBuffer == null) {
                writeBuffer = new char[WRITE_BUFFER_SIZE];
            }
            buffer = writeBuffer;
        } else {
            // Don't permanently allocate very large buffers.
            buffer = new char[len];
        }
        int newLen = 0;
        for (int i = off, j = 0, k = 0; k < len; i++, k++) {
            if (!filter.test(cbuf[i])) {
                buffer[j++] = cbuf[i];
                newLen++;
            }
        }
        output.write(buffer, 0, newLen);
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
