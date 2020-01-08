/*
 * DontCloseOutputStream.java
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
import java.io.OutputStream;

final class DontCloseOutputStream extends OutputStream {

    private OutputStream output;

    DontCloseOutputStream(OutputStream output) {
        this.output = output;
    }

    private void ensureOpen() throws IOException {
        if (output == null) {
            throw streamClosedException();
        }
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        output.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        ensureOpen();
        output.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        output.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        output.flush();
    }

    @Override
    public void close() throws IOException {
        // don't close output
        output = null;
    }
}
