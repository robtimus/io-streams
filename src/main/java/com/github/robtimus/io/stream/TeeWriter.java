/*
 * TeeWriter.java
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
import java.io.OutputStream;
import java.io.Writer;

/**
 * A writer that splits data to two other writers. This class is similar to
 * <a href="https://commons.apache.org/proper/commons-io/javadocs/api-release/org/apache/commons/io/output/TeeOutputStream.html">TeeOutputStream</a>
 * for {@link Writer} instead of {@link OutputStream}.
 *
 * @author Rob Spoor
 */
public final class TeeWriter extends Writer {

    private final Writer output;
    private final Writer tee;

    /**
     * Creates a new splitting writer.
     *
     * @param output The main writer.
     * @param tee The second writer.
     */
    public TeeWriter(Writer output, Writer tee) {
        this.output = output;
        this.tee = tee;
    }

    @Override
    public void write(int c) throws IOException {
        output.write(c);
        tee.write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        output.write(cbuf);
        tee.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        output.write(cbuf, off, len);
        tee.write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        output.write(str);
        tee.write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        output.write(str, off, len);
        tee.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        output.append(csq);
        tee.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        output.append(csq, start, end);
        tee.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        output.append(c);
        tee.append(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        output.flush();
        tee.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            output.close();
        } finally {
            tee.close();
        }
    }
}
