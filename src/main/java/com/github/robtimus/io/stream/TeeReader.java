/*
 * TeeReader.java
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

import static com.github.robtimus.io.stream.AppendableWriter.asWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/**
 * A reader that copies all text that is read to an {@code Appendable}. This class is similar to
 * <a href="https://commons.apache.org/proper/commons-io/javadocs/api-release/org/apache/commons/io/input/TeeInputStream.html">TeeInputStream</a> for
 * {@link Reader} and {@link Appendable} instead of {@link InputStream} and {@link OutputStream}, but it makes sure to not cause text to be skipped or
 * duplicated. To prevent text to be duplicated, {@link #mark(int)} and {@link #reset()} are only supported if the {@code Appendable} is a
 * {@link StringBuilder} or {@link StringBuffer}.
 *
 * @author Rob Spoor
 */
public final class TeeReader extends Reader {

    private final Reader input;
    private final Appendable tee;
    private final Writer teeWriter;
    private final boolean closeTee;

    private int mark;

    /**
     * Creates a new copying reader. It will not close the {@code Appendable} if this reader is closed.
     *
     * @param input The backing reader.
     * @param tee The {@code Appendable} to copy to.
     */
    public TeeReader(Reader input, Appendable tee) {
        this(input, tee, false);
    }

    /**
     * Creates a new copying reader.
     *
     * @param input The backing reader.
     * @param tee The {@code Appendable} to copy to.
     * @param closeTee {@code true} to close the {@code Appendable} when this reader is closed, or {@code false} to keep it open.
     *                     This flag is ggnored if the {@code Appendable} does not implement {@code Closeable} or {@code AutoCloseable}.
     */
    public TeeReader(Reader input, Appendable tee, boolean closeTee) {
        this.input = Objects.requireNonNull(input);
        this.tee = tee;
        this.teeWriter = asWriter(tee);
        this.closeTee = closeTee;

        mark = 0;
    }

    @Override
    public int read() throws IOException {
        int read = input.read();
        if (read != -1) {
            teeWriter.write(read);
        }
        return read;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int read = input.read(cbuf, off, len);
        if (read != -1) {
            teeWriter.write(cbuf, off, read);
        }
        return read;
    }

    @Override
    public boolean ready() throws IOException {
        return input.ready();
    }

    // support mark and reset only if the tee is a StringBuilder or StringBuffer, as these support deleting

    @Override
    public boolean markSupported() {
        return input.markSupported() && (tee instanceof StringBuilder || tee instanceof StringBuffer);
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        if (tee instanceof StringBuilder) {
            input.mark(readAheadLimit);
            mark = ((StringBuilder) tee).length();
        } else if (tee instanceof StringBuffer) {
            input.mark(readAheadLimit);
            mark = ((StringBuffer) tee).length();
        } else {
            super.mark(readAheadLimit);
        }
    }

    @Override
    public void reset() throws IOException {
        if (tee instanceof StringBuilder) {
            input.reset();
            StringBuilder sb = (StringBuilder) tee;
            sb.delete(mark, sb.length());
        } else if (tee instanceof StringBuffer) {
            input.reset();
            StringBuffer sb = (StringBuffer) tee;
            sb.delete(mark, sb.length());
        } else {
            super.reset();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            input.close();
        } finally {
            if (closeTee) {
                teeWriter.close();
            }
        }
    }
}
