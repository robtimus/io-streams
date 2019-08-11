/*
 * AsciiInputStream.java
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
import java.io.InputStream;
import java.io.Reader;
import java.util.Base64.Decoder;
import java.util.Objects;

/**
 * An input stream that wraps a reader that contains only ASCII characters.
 * If the wrapped reader contains any non-ASCII characters, the wrapping {@code AsciiInputStream} will throw an {@link IOException}.
 * <p>
 * This can be used in code that expects an input stream where a reader is available.
 * One such example is base64 decoding. Even though base64 is text, {@link Decoder} can only wrap {@link InputStream}. This method can help:
 * <pre>InputStream input = Base64.getDecoder().wrap(new AsciiInputStream(reader));</pre>
 * <p>
 * When an {@code AsciiInputStream} is closed, the wrapped reader will be closed as well.
 *
 * @author Rob Spoor
 */
public final class AsciiInputStream extends InputStream {

    private static final int READ_BUFFER_SIZE = 1024;

    private final Reader input;

    private char[] readBuffer;

    /**
     * Creates a new ASCII input stream.
     *
     * @param input The reader to wrap.
     * @throws NullPointerException If the given reader is {@code null}.
     */
    public AsciiInputStream(Reader input) {
        this.input = Objects.requireNonNull(input);
    }

    @Override
    public int read() throws IOException {
        int read = input.read();
        return read == -1 ? -1 : convert((char) read);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkOffsetAndLength(b, off, len);

        char[] c;
        if (len <= READ_BUFFER_SIZE) {
            if (readBuffer == null) {
                readBuffer = new char[READ_BUFFER_SIZE];
            }
            c = readBuffer;
        } else {
            // Don't permanently allocate very large buffers.
            c = new char[len];
        }

        int read = input.read(c, 0, len);
        if (read == -1) {
            return -1;
        }
        convert(c, b, off, read);
        return read;
    }

    @Override
    public int available() throws IOException {
        return input.ready() ? 1 : 0;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private byte convert(char c) throws IOException {
        // 0 <= c by definition
        if (c > 127) {
            throw new IOException(Messages.ascii.invalidChar.get(c));
        }
        // 0 <= c <= 127, valid ASCII
        return (byte) c;
    }

    private void convert(char[] c, byte[] b, int off, int len) throws IOException {
        for (int i = 0, j = off; i < len; i++, j++) {
            b[j] = convert(c[i]);
        }
    }
}
