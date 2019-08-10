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
import java.util.function.IntPredicate;

final class FilteringWriter extends Writer {

    private static final int WRITE_BUFFER_SIZE = 1024;

    private final Writer output;
    private final IntPredicate filter;

    private char[] writeBuffer;

    FilteringWriter(Writer output, IntPredicate filter) {
        this.output = output;
        this.filter = filter;
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
