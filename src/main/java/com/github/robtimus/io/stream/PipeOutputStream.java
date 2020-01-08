/*
 * PipeOutputStream.java
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
import java.io.OutputStream;

/**
 * An input stream that writes to a {@link BinaryPipe}.
 *
 * @author Rob Spoor
 */
public final class PipeOutputStream extends OutputStream {

    private final BinaryPipe pipe;

    PipeOutputStream(BinaryPipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the pipe that this output stream is writing to.
     *
     * @return The pipe that this output stream is writing to.
     */
    public BinaryPipe pipe() {
        return pipe;
    }

    @Override
    public void write(int b) throws IOException {
        pipe.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        pipe.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        pipe.flush();
    }

    /**
     * Closes this output stream. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to this output stream
     * will cause an exception to be thrown. The pipe's input stream will continue to consume the data that has been written; if all is consumed it
     * will return {@code -1} from its read methods, unless if {@link #close(IOException)} is called with a non-{@code null} error.
     * <p>
     * This method is like {@link #close(IOException)}, except it does not change the exception thrown from the pipe's input stream's methods.
     * This allows the use of {@link #close(IOException)} inside try-with-resources blocks without the automatic closing resetting the error.
     * <p>
     * It is possible to call {@code close()} and {@link #close(IOException)} several times. This can be used to change or clear the exception to
     * throw when reading from the pipe's input stream.
     */
    @Override
    public void close() {
        pipe.closeOutput();
    }

    /**
     * Closes this output stream. After calling this method, the {@link #pipe() pipe} will be closed, and attempting to write to this output stream
     * will cause an exception to be thrown. If an exception is given, attempting to read from the pipe's input stream will cause this exception to be
     * thrown. Otherwise, the pipe's input stream will continue to consume the data that has been written; if all is consumed it will return
     * {@code -1} from its read methods.
     * <p>
     * It is possible to call {@link #close()} and {@code close(IOException)} several times. This can be used to change or clear the exception to
     * throw when reading from the pipe's input stream.
     *
     * @param error If not {@code null}, the exception to throw when reading from the pipe's input stream.
     *                  Otherwise, the input stream will continue to consume the data that has been written.
     */
    public void close(IOException error) {
        pipe.closeOutput(error);
    }
}
