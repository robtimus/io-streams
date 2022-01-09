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
import java.util.function.BiConsumer;
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

    private Consumer<? super CapturingInputStream> doneCallback;
    private Consumer<? super CapturingInputStream> limitReachedCallback;
    private final BiConsumer<? super CapturingInputStream, ? super IOException> errorCallback;

    /**
     * Creates a new capturing input stream.
     *
     * @param input The input stream to capture from.
     * @param config The configuration to use.
     * @throws NullPointerException If the given input stream or config is {@code null}.
     */
    public CapturingInputStream(InputStream input, Config config) {
        delegate = Objects.requireNonNull(input);

        captor = config.expectedCount < 0 ? new ByteCaptor() : new ByteCaptor(Math.min(config.expectedCount, config.limit));
        limit = config.limit;
        doneAfter = config.doneAfter;

        doneCallback = config.doneCallback;
        limitReachedCallback = config.limitReachedCallback;
        errorCallback = config.errorCallback;
    }

    @Override
    public int read() throws IOException {
        try {
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

        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        try {
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

        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
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

        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    // don't delegate skip, so no content is lost

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
            markAsClosed();
        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        delegate.mark(readlimit);
        mark = totalBytes;
    }

    @Override
    public synchronized void reset() throws IOException {
        try {
            delegate.reset();
            captor.reset((int) Math.min(mark, limit));
            totalBytes = mark;
            consumed = false;

        } catch (IOException e) {
            onError(e);
            throw e;
        }
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

    private void onError(IOException error) {
        if (errorCallback != null) {
            errorCallback.accept(this, error);
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
    public static Config.Builder config() {
        return new Config.Builder();
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

        private final Consumer<? super CapturingInputStream> doneCallback;
        private final Consumer<? super CapturingInputStream> limitReachedCallback;
        private final BiConsumer<? super CapturingInputStream, ? super IOException> errorCallback;

        private Config(Builder builder) {
            limit = builder.limit;

            expectedCount = builder.expectedCount;

            doneAfter = builder.doneAfter;

            doneCallback = builder.doneCallback;
            limitReachedCallback = builder.limitReachedCallback;
            errorCallback = builder.errorCallback;
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

            private Consumer<? super CapturingInputStream> doneCallback;
            private Consumer<? super CapturingInputStream> limitReachedCallback;
            private BiConsumer<? super CapturingInputStream, ? super IOException> errorCallback;

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
             * Sets the number of bytes after which built capturing input streams are considered to be done. The default is {@link Long#MAX_VALUE}.
             * <p>
             * Some frameworks don't fully consume all content. Instead they stop at a specific point. For instance, some JSON parsers stop reading as
             * soon as the root object's closing closing curly brace is encountered.
             * <p>
             * Ideally such a framework is configured to consume all content. This method can be used as fallback if that's not possible.
             * For instance, it can be called with an HTTP request's content length.
             *
             * @param count The number of bytes after which to consider built capturing input streams as done.
             * @return This object.
             * @throws IllegalArgumentException If the given number of bytes is negative.
             */
            public Builder doneAfter(long count) {
                if (count < 0) {
                    throw new IllegalArgumentException(count + " < 0"); //$NON-NLS-1$
                }
                doneAfter = count;
                return this;
            }

            /**
             * Sets a callback that will be triggered when reading from built capturing input streams is done. This can be because the input stream is
             * {@link CapturingInputStream#isConsumed() consumed} or {@link CapturingInputStream#isClosed() closed}, or because the amount set using
             * {@link #doneAfter(long)} has been reached.
             * A capturing input stream will only trigger its callback once.
             *
             * @param callback The callback to set.
             * @return This object.
             * @throws NullPointerException If the given callback is {@code null}.
             */
            public Builder onDone(Consumer<? super CapturingInputStream> callback) {
                doneCallback = Objects.requireNonNull(callback);
                return this;
            }

            /**
             * Sets a callback that will be triggered when built capturing input streams hit their limit. If an input stream never reaches its limit
             * its callback will never be called.
             * <p>
             * In case a capturing input stream has reached its limit and is then {@link CapturingInputStream#reset()} to before its limit, it will
             * not call its callback again.
             *
             * @param callback The callback to set.
             * @return This object.
             * @throws NullPointerException If the given callback is {@code null}.
             */
            public Builder onLimitReached(Consumer<? super CapturingInputStream> callback) {
                limitReachedCallback = Objects.requireNonNull(callback);
                return this;
            }

            /**
             * Sets a callback that will be triggered when an {@link IOException} occurs while using built capturing input streams.
             * A capturing input stream can trigger its error callback multiple times.
             *
             * @param callback The callback to set.
             * @return This object.
             * @throws NullPointerException If the given callback is {@code null}.
             */
            public Builder onError(BiConsumer<? super CapturingInputStream, ? super IOException> callback) {
                errorCallback = Objects.requireNonNull(callback);
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
}
