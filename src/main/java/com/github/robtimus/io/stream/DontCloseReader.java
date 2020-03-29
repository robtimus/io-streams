/*
 * DontCloseReader.java
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

import static com.github.robtimus.io.stream.StreamUtils.streamClosedException;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Objects;

final class DontCloseReader extends Reader {

    private Reader input;

    DontCloseReader(Reader input) {
        this.input = Objects.requireNonNull(input);
    }

    private void ensureOpen() throws IOException {
        if (input == null) {
            throw streamClosedException();
        }
    }

    @Override
    public int read(CharBuffer target) throws IOException {
        ensureOpen();
        return input.read(target);
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        return input.read();
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        ensureOpen();
        return input.read(cbuf);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        ensureOpen();
        return input.read(cbuf, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        ensureOpen();
        return input.skip(n);
    }

    @Override
    public boolean ready() throws IOException {
        ensureOpen();
        return input.ready();
    }

    @Override
    public boolean markSupported() {
        return input != null && input.markSupported();
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        ensureOpen();
        input.mark(readAheadLimit);
    }

    @Override
    public void reset() throws IOException {
        ensureOpen();
        input.reset();
    }

    @Override
    public void close() throws IOException {
        // don't close input
        input = null;
    }
}
