/*
 * PipeReader.java
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
import java.io.Reader;

/**
 * A reader that reads from a {@link TextPipe}.
 *
 * @author Rob Spoor
 */
public final class PipeReader extends Reader {

    private final TextPipe pipe;

    PipeReader(TextPipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the pipe that this reader is reading from.
     *
     * @return The pipe that this reader is reading from.
     */
    public TextPipe pipe() {
        return pipe;
    }

    @Override
    public int read() throws IOException {
        return pipe.read();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return pipe.read(cbuf, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return pipe.skip(n);
    }

    @Override
    public boolean ready() throws IOException {
        return pipe.ready();
    }

    /**
     * Closes this reader. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to the pipe's writer will cause
     * an exception to be thrown. When reading from this reader, {@code -1} is returned, except if an exception is set using
     * {@link PipeWriter#close(IOException)}.
     * <p>
     * This method is like {@link #close(IOException)}, except it does not change the exception thrown from the pipe's writer's methods.
     * This allows the use of {@link #close(IOException)} inside try-with-resources blocks without the automatic closing resetting the error.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change the exception to throw when
     * writing to the pipe's writer.
     */
    @Override
    public void close() {
        pipe.closeInput();
    }

    /**
     * Closes this reader. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to the pipe's writer will cause
     * an exception to be thrown. When reading from this reader, {@code -1} is returned, except if an exception is set using
     * {@link PipeWriter#close(IOException)}.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change the exception to throw when
     * writing to the pipe's writer.
     *
     * @param error If not {@code null}, the exception to throw when writing to the pipe's writer.
     *                  Otherwise an exception is thrown that indicates the writer is closed.
     */
    public void close(IOException error) {
        pipe.closeInput(error);
    }
}
