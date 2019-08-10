/*
 * FilteringInputStream.java
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
import java.io.InputStream;
import java.util.function.IntPredicate;

final class FilteringInputStream extends InputStream {

    private final InputStream input;
    private final IntPredicate filter;

    FilteringInputStream(InputStream input, IntPredicate filter) {
        this.input = input;
        this.filter = filter;
    }

    @Override
    public int read() throws IOException {
        int read = input.read();
        while (read != -1 && filter.test((byte) read)) {
            read = input.read();
        }
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = input.read(b, off, len);
        if (read == -1) {
            return -1;
        }
        int result = 0;
        for (int i = off, j = off, k = 0; k < read; i++, k++) {
            if (!filter.test(b[i])) {
                b[j++] = b[i];
                result++;
            }
        }
        return result;
    }

    @Override
    public int available() throws IOException {
        // don't delegate to input, as its available bytes may all be filtered out
        return 0;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        input.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        input.reset();
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }
}
