/*
 * CapturingInputStream.java
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An input stream that captures the content it reads.
 * This can be useful for input streams that are handed over to other code, where it is unknown when the input stream is consumed.
 * Using callbacks this class allows code to be executed when the stream has been fully consumed.
 * <p>
 * An example use for this class can be logging in an HTTP filter or HTTP client. Instead of copying the contents of an input stream to memory,
 * logging the contents, and then passing a {@link ByteArrayInputStream} with the copied contents, you can create a capturing input stream with a
 * callback that performs the logging.
 * <p>
 * {@code CapturingInputStream} supports {@link #mark(int)} and {@link #reset()} if its backing input stream does. When {@link #reset()} is called, it
 * will "uncapture" any contents up to the previous mark. It will still only once call any of the callbacks.
 *
 * @author Rob Spoor
 */
public final class CapturingInputStream extends InputStream {

    private final InputStream delegate;

    private final ByteCaptor captor;
    private final int limit;
    private final long doneAfter;

    private long totalBytes = 0;
    private long mark = 0;

    private boolean consumed = false;
    private boolean closed = false;

    private Consumer<CapturingInputStream> doneCallback;
    private Consumer<CapturingInputStream> limitReachedCallback;

    /**
     * Creates a new capturing input stream.
     *
     * @param input The input stream to capture from.
     * @param config The configuration to use.
     * @throws NullPointerException If the given input stream or config is {@code null}.
     */
    public CapturingInputStream(InputStream input, Config config) {
        this.delegate = Objects.requireNonNull(input);

        captor = config.expectedCount < 0 ? new ByteCaptor() : new ByteCaptor(Math.min(config.expectedCount, config.limit));
        limit = config.limit;
        doneAfter = config.doneAfter;

        doneCallback = config.doneCallback;
        limitReachedCallback = config.limitReachedCallback;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        if (b == -1) {
            markAsConsumed();
        } else {
            totalBytes++;

            if (captor.size() < limit) {
                captor.write(b);
                checkLimitReached();
            }
            checkDone();
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = delegate.read(b);
        if (n == -1) {
            markAsConsumed();
        } else {
            totalBytes += n;

            int allowed = Math.min(limit - captor.size(), n);
            if (allowed > 0) {
                captor.write(b, 0, allowed);
                checkLimitReached();
            }
            checkDone();
        }
        return n;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = delegate.read(b, off, len);
        if (n == -1) {
            markAsConsumed();
        } else {
            totalBytes += n;

            int allowed = Math.min(limit - captor.size(), n);
            if (allowed > 0) {
                captor.write(b, off, allowed);
                checkLimitReached();
            }
            checkDone();
        }
        return n;
    }

    // don't delegate skip, so no content is lost

    @Override
    public void close() throws IOException {
        delegate.close();
        markAsClosed();
    }

    @Override
    public synchronized void mark(int readlimit) {
        delegate.mark(readlimit);
        mark = totalBytes;
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
        captor.reset((int) Math.min(mark, limit));
        totalBytes = mark;
        consumed = false;
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    private void markAsConsumed() {
        consumed = true;
        if (doneCallback != null) {
            doneCallback.accept(this);
            doneCallback = null;
        }
    }

    private void markAsClosed() {
        closed = true;
        if (doneCallback != null) {
            doneCallback.accept(this);
            doneCallback = null;
        }
    }

    private void checkLimitReached() {
        if (totalBytes >= limit && limitReachedCallback != null) {
            limitReachedCallback.accept(this);
            limitReachedCallback = null;
        }
    }

    private void checkDone() {
        if (totalBytes >= doneAfter && doneCallback != null) {
            doneCallback.accept(this);
            doneCallback = null;
        }
    }

    /**
     * Returns the contents that have been captured.
     *
     * @return An array with the bytes that have been captured.
     */
    public byte[] captured() {
        return captor.toByteArray();
    }

    /**
     * Returns the contents that have been captured, as a string.
     *
     * @param charset The charset to use.
     * @return A string representing the contents that have been captured.
     */
    public String captured(Charset charset) {
        return captor.toString(charset);
    }

    /**
     * Returns the total number of bytes that have been read.
     *
     * @return The total number of bytes that have been read.
     */
    public long totalBytes() {
        return totalBytes;
    }

    /**
     * Returns whether or not this input stream has been fully consumed.
     * In other words, returns whether or not one of the read methods has been called and returned {@code -1}.
     *
     * @return {@code true} if this input stream has been fully consumed, or {@code false} otherwise.
     */
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * Returns whether or not this input stream has been closed.
     *
     * @return {@code true} if this input stream has been closed, or {@code false} otherwise.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Creates a builder for capturing input stream configurations.
     *
     * @return The created builder.
     */
    public static Builder config() {
        return new Builder();
    }

    /**
     * Configuration for {@link CapturingInputStream capturing input streams}.
     *
     * @author Rob Spoor
     */
    public static final class Config {

        private final int limit;

        private final int expectedCount;

        private final long doneAfter;

        private final Consumer<CapturingInputStream> doneCallback;
        private final Consumer<CapturingInputStream> limitReachedCallback;

        private Config(Builder builder) {
            this.limit = builder.limit;

            this.expectedCount = builder.expectedCount;

            this.doneAfter = builder.doneAfter;

            this.doneCallback = builder.doneCallback;
            this.limitReachedCallback = builder.limitReachedCallback;
        }
    }

    /**
     * A builder for {@link Config capturing input stream configurations}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private int limit = Integer.MAX_VALUE;

        private int expectedCount = -1;

        private long doneAfter = Long.MAX_VALUE;

        private Consumer<CapturingInputStream> doneCallback;
        private Consumer<CapturingInputStream> limitReachedCallback;

        private Builder() {
        }

        /**
         * Sets the maximum number of bytes to capture. The default value is {@link Integer#MAX_VALUE}.
         *
         * @param limit The maximum number of bytes to capture.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         */
        public Builder withLimit(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            this.limit = limit;
            return this;
        }

        /**
         * Sets the expected number of bytes that can be read from the wrapped input stream.
         * This can be used for performance reasons; if this is set then the capture buffer will be pre-allocated.
         * The default value is {@code -1}.
         *
         * @param expectedCount The expected number of bytes that can be read from the wrapped input stream, or a negative number if not known.
         * @return This object.
         */
        public Builder withExpectedCount(int expectedCount) {
            this.expectedCount = expectedCount;
            return this;
        }

        /**
         * Sets a callback that will be triggered when reading from built capturing input streams is done. This can be because the input stream is
         * {@link CapturingInputStream#isConsumed() consumed} or {@link CapturingInputStream#isClosed() closed}.
         * A capturing input stream will only trigger its callback once.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder whenDone(Consumer<CapturingInputStream> callback) {
            return whenDoneAfter(Long.MAX_VALUE, callback);
        }

        /**
         * Sets a callback that will be triggered when reading from built capturing input streams is done. This can be because the input stream is
         * {@link CapturingInputStream#isConsumed() consumed} or {@link CapturingInputStream#isClosed() closed}.
         * A capturing input stream will only trigger its callback once.
         * <p>
         * Some frameworks don't fully consume all content. Instead they stop after a specific number of bytes has been read, e.g. based on the
         * content length of HTTP requests. This method allows a marker to be defined that, when reached, will trigger the callback, even if the
         * stream hasn't been fully consumed or closed.
         *
         * @param doneAfter The number of bytes after which to trigger the callback.
         * @param callback The callback to set.
         * @return This object.
         * @throws IllegalArgumentException If the given number of bytes is negative.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder whenDoneAfter(long doneAfter, Consumer<CapturingInputStream> callback) {
            if (doneAfter < 0) {
                throw new IllegalArgumentException(doneAfter + " < 0"); //$NON-NLS-1$
            }
            doneCallback = Objects.requireNonNull(callback);
            this.doneAfter = doneAfter;
            return this;
        }

        /**
         * Sets a callback that will be triggered when built capturing input streams hit their limit. If an input stream never reaches its limit its
         * callback will never be called.
         * <p>
         * In case a capturing input stream has reached its limit and is then {@link CapturingInputStream#reset()} to before its limit, it will not
         * call its callback again.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder whenLimitReached(Consumer<CapturingInputStream> callback) {
            limitReachedCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Creates a new {@link Config capturing input stream configuration} with the settings from this builder.
         *
         * @return The created capturing input stream configuration.
         */
        public Config build() {
            return new Config(this);
        }
    }
}
