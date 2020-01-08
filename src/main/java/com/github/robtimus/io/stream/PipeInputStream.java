/*
 * PipeInputStream.java
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

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that reads from a {@link BinaryPipe}.
 *
 * @author Rob Spoor
 */
public final class PipeInputStream extends InputStream {

    private final BinaryPipe pipe;

    PipeInputStream(BinaryPipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the pipe that this input stream is reading from.
     *
     * @return The pipe that this input stream is reading from.
     */
    public BinaryPipe pipe() {
        return pipe;
    }

    @Override
    public int read() throws IOException {
        return pipe.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return pipe.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return pipe.skip(n);
    }

    @Override
    public int available() throws IOException {
        return pipe.available();
    }

    /**
     * Closes this input stream. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to the pipe's output
     * stream will cause an exception to be thrown. When reading from this input stream, {@code -1} is returned, except if an exception is set using
     * {@link PipeOutputStream#close(IOException)}.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change the exception to throw when
     * writing to the pipe's output stream.
     */
    @Override
    public void close() {
        pipe.closeInput();
    }

    /**
     * Closes this input stream. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to the pipe's output
     * stream will cause an exception to be thrown. When reading from this input stream, {@code -1} is returned, except if an exception is set using
     * {@link PipeOutputStream#close(IOException)}.
     * <p>
     * It is possible to call {@link #close()} and {@code close(IOException)} several times. This can be used to change the exception to throw when
     * writing to the pipe's output stream.
     *
     * @param error If not {@code null}, the exception to throw when writing to the pipe's output stream.
     *                  Otherwise an exception is thrown that indicates the stream is closed.
     */
    public void close(IOException error) {
        pipe.closeInput(error);
    }
}
