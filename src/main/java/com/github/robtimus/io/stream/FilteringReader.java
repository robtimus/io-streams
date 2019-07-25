/*
 * FilteringReader.java
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

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * An reader that filters the contents of another reader.
 * For instance, to create a reader that does not return any whitespace characters, call {@code new FilteringReader(input, Character::isWhitespace)}.
 * <p>
 * When a filtering reader is closed, the wrapped reader will also be closed.
 *
 * @author Rob Spoor
 */
public final class FilteringReader extends Reader {

    private final Reader input;
    private final IntPredicate filter;

    /**
     * Creates a new filtering reader.
     *
     * @param input The {@code Reader} to wrap.
     * @param filter The predicate to use to filter out bytes.
     *                   Any byte for which the predicate's {@link IntPredicate#test(int) test} method returns {@code true} will be filtered out.
     * @throws NullPointerException If the given {@code Reader} or predicate is {@code null}.
     */
    public FilteringReader(Reader input, IntPredicate filter) {
        this.input = Objects.requireNonNull(input);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public int read() throws IOException {
        int read = input.read();
        while (read != -1 && filter.test((char) read)) {
            read = input.read();
        }
        return read;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int read = input.read(cbuf, off, len);
        if (read == -1) {
            return -1;
        }
        int result = 0;
        for (int i = off, j = off, k = 0; k < read; i++, k++) {
            if (!filter.test(cbuf[i])) {
                cbuf[j++] = cbuf[i];
                result++;
            }
        }
        return result;
    }

    @Override
    public boolean ready() throws IOException {
        // don't delegate to input, as the characters it can return before blocking may all be filtered out
        return false;
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        input.mark(readAheadLimit);
    }

    @Override
    public void reset() throws IOException {
        input.reset();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
