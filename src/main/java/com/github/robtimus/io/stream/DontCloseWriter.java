/*
 * DontCloseWriter.java
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
import java.io.Writer;

final class DontCloseWriter extends Writer {

    private Writer output;

    DontCloseWriter(Writer output) {
        this.output = output;
    }

    private void ensureOpen() throws IOException {
        if (output == null) {
            throw streamClosedException();
        }
    }

    @Override
    public void write(int c) throws IOException {
        ensureOpen();
        output.write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        ensureOpen();
        output.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        ensureOpen();
        output.write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        ensureOpen();
        output.write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        ensureOpen();
        output.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        ensureOpen();
        output.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        ensureOpen();
        output.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        ensureOpen();
        output.append(c);
        return this;
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
