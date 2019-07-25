/*
 * DontCloseInputStream.java
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

import static com.github.robtimus.io.stream.StreamUtils.streamClosedException;
import java.io.IOException;
import java.io.InputStream;

final class DontCloseInputStream extends InputStream {

    private InputStream input;

    DontCloseInputStream(InputStream input) {
        this.input = input;
    }

    private void ensureOpen() throws IOException {
        if (input == null) {
            throw streamClosedException();
        }
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        return input.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        ensureOpen();
        return input.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        return input.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        ensureOpen();
        return input.skip(n);
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return input.available();
    }

    @Override
    public void close() throws IOException {
        // don't close input
        input = null;
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (input != null) {
            input.mark(readlimit);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        ensureOpen();
        input.reset();
    }

    @Override
    public boolean markSupported() {
        return input != null && input.markSupported();
    }
}
