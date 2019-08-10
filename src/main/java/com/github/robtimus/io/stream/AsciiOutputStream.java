/*
 * AsciiOutputStream.java
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
import java.io.OutputStream;
import java.io.Writer;

final class AsciiOutputStream extends OutputStream {

    private static final int WRITE_BUFFER_SIZE = 1024;

    private final Writer output;

    private char[] writeBuffer;

    AsciiOutputStream(Writer output) {
        this.output = output;
    }

    @Override
    public void write(int b) throws IOException {
        output.write(convert((byte) b));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkOffsetAndLength(b, off, len);

        char[] c;
        if (len <= WRITE_BUFFER_SIZE) {
            if (writeBuffer == null) {
                writeBuffer = new char[WRITE_BUFFER_SIZE];
            }
            c = writeBuffer;
        } else {
            // Don't permanently allocate very large buffers.
            c = new char[len];
        }

        convert(b, c, off, len);
        output.write(c, 0, len);
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    private char convert(byte b) throws IOException {
        // b <= 127 by definition
        if (b < 0) {
            throw new IOException(Messages.ascii.invalidByte.get(b));
        }
        // 0 <= b <= 127, valid ASCII
        return (char) b;
    }

    private void convert(byte[] b, char[] c, int off, int len) throws IOException {
        for (int i = off, j = 0; j < len; i++, j++) {
            c[j] = convert(b[i]);
        }
    }
}
