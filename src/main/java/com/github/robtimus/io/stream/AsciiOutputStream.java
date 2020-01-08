/*
 * AsciiOutputStream.java
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
import java.io.Writer;
import java.util.Base64.Encoder;
import java.util.Objects;

/**
 * An output stream that wraps a writer to write only ASCII characters.
 * If any non-ASCII bytes are written to an {@code AsciiOutputStream}, it will thrown an {@link IOException}.
 * <p>
 * This can be used in code that expects an output stream where a writer is available.
 * One such example is base64 encoding. Even though base64 is text, {@link Encoder} can only wrap {@link OutputStream}. This method can help:
 * <pre>OutputStream output = Base64.getEncoder().wrap(new AsciiOutputStream(writer));</pre>
 * <p>
 * When an {@code AsciiOutputStream} is closed, the wrapped writer will be closed as well.
 *
 * @author Rob Spoor
 */
public final class AsciiOutputStream extends OutputStream {

    private static final int WRITE_BUFFER_SIZE = 1024;

    private final Writer output;

    private char[] writeBuffer;

    /**
     * Creates a new ASCII output stream.
     *
     * @param output The writer to wrap.
     * @throws NullPointerException If the given writer is {@code null}.
     */
    public AsciiOutputStream(Writer output) {
        this.output = Objects.requireNonNull(output);
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
