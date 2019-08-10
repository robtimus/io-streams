/*
 * PipeWriter.java
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
import java.io.Writer;

/**
 * A writer that writes to a {@link TextPipe}.
 *
 * @author Rob Spoor
 */
public final class PipeWriter extends Writer {

    private final TextPipe pipe;

    PipeWriter(TextPipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the pipe that this writer is writing to.
     *
     * @return The pipe that this writer is writing to.
     */
    public TextPipe pipe() {
        return pipe;
    }

    @Override
    public void write(int b) throws IOException {
        pipe.write(b);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        pipe.write(cbuf, off, len);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        pipe.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        pipe.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        pipe.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        write(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        pipe.flush();
    }

    /**
     * Closes this writer. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to this writer will cause an
     * exception to be thrown. The pipe's reader will continue to consume the data that has been written; if all is consumed it will return {@code -1}
     * from its read methods, unless if {@link #close(IOException)} is called with a non-{@code null} error.
     * <p>
     * This method is like {@link #close(IOException)}, except it does not change the exception thrown from the pipe's reader's methods.
     * This allows the use of {@link #close(IOException)} inside try-with-resources blocks without the automatic closing resetting the error.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change or clear the exception to
     * throw when reading from the pipe's reader.
     */
    @Override
    public void close() {
        pipe.closeOutput();
    }

    /**
     * Closes this writer. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to this writer will cause an
     * exception to be thrown. If an exception is given, attempting to read from the pipe's reader will cause this exception to be thrown.
     * Otherwise, the pipe's reader will continue to consume the data that has been written; if all is consumed it will return {@code -1} from its
     * read methods.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change or clear the exception to
     * throw when reading from the pipe's reader.
     *
     * @param error If not {@code null}, the exception to throw when reading from the pipe's reader.
     *                  Otherwise, the reader will continue to consume the data that has been written.
     */
    public void close(IOException error) {
        pipe.closeOutput(error);
    }
}
