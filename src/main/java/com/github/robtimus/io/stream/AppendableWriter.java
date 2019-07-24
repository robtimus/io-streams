/*
 * AppendableWriter.java
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * A {@link Writer} wrapper around an {@link Appendable}. It will delegate all calls to the wrapped {@code Appendable}.
 * This includes {@link #flush()} if the wrapped {@code Appendable} implements {@link Flushable}, and {@link #close()} if the wrapped
 * {@code Appendable} implements {@link Closeable} or {@link AutoCloseable}.
 * <p>
 * Note that the behaviour of closing an {@code AppendableWriter} depends on the wrapped {@code Appendable}. If it does not support closing, or if it
 * still allows text to be appended after closing, then the closed {@code AppendableWriter} allows text to be appended after closing. If it does not
 * allow text to be appended after closing, then neither will the closed {@code AppendableWriter}.
 *
 * @author Rob Spoor
 */
public final class AppendableWriter extends Writer {

    private final Appendable appendable;
    private CharArraySequence array;

    private AppendableWriter(Appendable appendable) {
        this.appendable = appendable;
    }

    @Override
    public void write(int c) throws IOException {
        appendable.append((char) c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        if (array == null) {
            array = new CharArraySequence();
        }
        array.reset(cbuf);
        appendable.append(array);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (array == null) {
            array = new CharArraySequence();
        }
        array.resetWithOffsetAndLength(cbuf, off, len);
        appendable.append(array);
    }

    @Override
    public void write(String str) throws IOException {
        Objects.requireNonNull(str);
        appendable.append(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        Objects.requireNonNull(str);
        appendable.append(str, off, off + len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        appendable.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        appendable.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        appendable.append(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        if (appendable instanceof Flushable) {
            ((Flushable) appendable).flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (appendable instanceof Closeable) {
            ((Closeable) appendable).close();
        } else if (appendable instanceof AutoCloseable) {
            try {
                ((AutoCloseable) appendable).close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * Returns a {@code Writer} for an {@code Appendable}.
     *
     * @param appendable The {@code Appendable} to return a {@code Writer} for.
     * @return The given {@code Appendable} itself if it's already a {@code Writer}, otherwise a wrapper around the given {@code Appendable}.
     * @throws NullPointerException If the given {@code Appendable} is {@code null}.
     */
    public static Writer asWriter(Appendable appendable) {
        Objects.requireNonNull(appendable);
        return appendable instanceof Writer ? (Writer) appendable : new AppendableWriter(appendable);
    }
}
