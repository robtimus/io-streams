/*
 * CapturingReader.java
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
import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A reader that captures the content it reads.
 * This can be useful for readers that are handed over to other code, where it is unknown when the reader is consumed.
 * Using callbacks this class allows code to be executed when the stream has been fully consumed.
 * <p>
 * An example use for this class can be logging in an HTTP filter or HTTP client. Instead of copying the contents of a reader to memory, logging the
 * contents, and then passing a {@link StringReader} with the copied contents, you can create a capturing reader with a callback that performs the
 * logging.
 * <p>
 * {@code CapturingReader} supports {@link #mark(int)} and {@link #reset()} if its backing reader does. When {@link #reset()} is called, it will
 * "uncapture" any contents up to the previous mark. It will still only once call any of the callbacks.
 *
 * @author Rob Spoor
 */
public final class CapturingReader extends Reader {

    private final Reader delegate;

    private final StringBuilder captor;
    private final int limit;
    private final long doneAfter;

    private long totalChars = 0;
    private long mark = 0;

    private boolean consumed = false;
    private boolean closed = false;

    private Consumer<? super CapturingReader> doneCallback;
    private Consumer<? super CapturingReader> limitReachedCallback;
    private final BiConsumer<? super CapturingReader, ? super IOException> errorCallback;

    /**
     * Creates a new capturing reader.
     *
     * @param input The reader to capture from.
     * @param config The configuration to use.
     * @throws NullPointerException If the given reader or config is {@code null}.
     */
    public CapturingReader(Reader input, Config config) {
        delegate = Objects.requireNonNull(input);

        captor = config.expectedCount < 0 ? new StringBuilder() : new StringBuilder(Math.min(config.expectedCount, config.limit));
        limit = config.limit;
        doneAfter = config.doneAfter;

        doneCallback = config.doneCallback;
        limitReachedCallback = config.limitReachedCallback;
        errorCallback = config.errorCallback;
    }

    // don't delegate read(CharBuffer), the default implementation is good enough

    @Override
    public int read() throws IOException {
        try {
            int c = delegate.read();
            if (c == -1) {
                markAsConsumed();
            } else {
                totalChars++;

                if (captor.length() < limit) {
                    captor.append((char) c);
                    checkLimitReached();
                }
                checkDone();
            }
            return c;

        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    @Override
    public int read(char[] c) throws IOException {
        try {
            int n = delegate.read(c);
            if (n == -1) {
                markAsConsumed();
            } else {
                totalChars += n;

                int allowed = Math.min(limit - captor.length(), n);
                if (allowed > 0) {
                    captor.append(c, 0, allowed);
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
    public int read(char[] c, int off, int len) throws IOException {
        try {
            int n = delegate.read(c, off, len);
            if (n == -1) {
                markAsConsumed();
            } else {
                totalChars += n;

                int allowed = Math.min(limit - captor.length(), n);
                if (allowed > 0) {
                    captor.append(c, off, allowed);
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
    public void mark(int readlimit) throws IOException {
        try {
            delegate.mark(readlimit);
            mark = totalChars;
        } catch (IOException e) {
            onError(e);
            throw e;
        }
    }

    @Override
    public void reset() throws IOException {
        try {
            delegate.reset();
            captor.delete((int) Math.min(mark, limit), captor.length());
            totalChars = mark;
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
        if (totalChars >= limit && limitReachedCallback != null) {
            limitReachedCallback.accept(this);
            limitReachedCallback = null;
        }
    }

    private void checkDone() {
        if (totalChars >= doneAfter && doneCallback != null) {
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
     * @return A string representing the contents that have been captured.
     */
    public String captured() {
        return captor.toString();
    }

    /**
     * Returns the total number of characters that have been read.
     *
     * @return The total number of characters that have been read.
     */
    public long totalChars() {
        return totalChars;
    }

    /**
     * Returns whether or not this reader has been fully consumed.
     * In other words, returns whether or not one of the read methods has been called and returned {@code -1}.
     *
     * @return {@code true} if this reader has been fully consumed, or {@code false} otherwise.
     */
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * Returns whether or not this reader has been closed.
     *
     * @return {@code true} if this reader has been closed, or {@code false} otherwise.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Creates a builder for capturing reader configurations.
     *
     * @return The created builder.
     */
    public static Builder config() {
        return new Builder();
    }

    /**
     * Configuration for {@link CapturingReader capturing readers}.
     *
     * @author Rob Spoor
     */
    public static final class Config {

        private final int limit;

        private final int expectedCount;

        private final long doneAfter;

        private final Consumer<? super CapturingReader> doneCallback;
        private final Consumer<? super CapturingReader> limitReachedCallback;
        private final BiConsumer<? super CapturingReader, ? super IOException> errorCallback;

        private Config(Builder builder) {
            limit = builder.limit;

            expectedCount = builder.expectedCount;

            doneAfter = builder.doneAfter;

            doneCallback = builder.doneCallback;
            limitReachedCallback = builder.limitReachedCallback;
            errorCallback = builder.errorCallback;
        }
    }

    /**
     * A builder for {@link Config capturing reader configurations}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private int limit = Integer.MAX_VALUE;

        private int expectedCount = -1;

        private long doneAfter = Long.MAX_VALUE;

        private Consumer<? super CapturingReader> doneCallback;
        private Consumer<? super CapturingReader> limitReachedCallback;
        private BiConsumer<? super CapturingReader, ? super IOException> errorCallback;

        private Builder() {
        }

        /**
         * Sets the maximum number of characters to capture. The default value is {@link Integer#MAX_VALUE}.
         *
         * @param limit The maximum number of characters to capture.
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
         * Sets the expected number of characters that can be read from the wrapped reader.
         * This can be used for performance reasons; if this is set then the capture buffer will be pre-allocated.
         * The default value is {@code -1}.
         *
         * @param expectedCount The expected number of characters that can be read from the wrapped reader, or a negative number if not known.
         * @return This object.
         */
        public Builder withExpectedCount(int expectedCount) {
            this.expectedCount = expectedCount;
            return this;
        }

        /**
         * Sets the number of characters after which built capturing readers are considered to be done. The default is {@link Long#MAX_VALUE}.
         * <p>
         * Some frameworks don't fully consume all content. Instead they stop at a specific point. For instance, some JSON parsers stop reading as
         * soon as the root object's closing closing curly brace is encountered.
         * <p>
         * Ideally such a framework is configured to consume all content. This method can be used as fallback if that's not possible.
         * For instance, it can be called with an HTTP request's content length.
         *
         * @param count The number of characters after which to consider built capturing readers as done.
         * @return This object.
         * @throws IllegalArgumentException If the given number of characters is negative.
         */
        public Builder doneAfter(long count) {
            if (count < 0) {
                throw new IllegalArgumentException(count + " < 0"); //$NON-NLS-1$
            }
            doneAfter = count;
            return this;
        }

        /**
         * Sets a callback that will be triggered when reading from built capturing readers is done. This can be because the reader is
         * {@link CapturingReader#isConsumed() consumed} or {@link CapturingReader#isClosed() closed}, or because the amount set using
         * {@link #doneAfter(long)} has been reached.
         * A capturing reader will only trigger its callback once.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder onDone(Consumer<? super CapturingReader> callback) {
            doneCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Sets a callback that will be triggered when built capturing readers hit their limit. If a reader never reaches its limit its callback will
         * never be called.
         * <p>
         * In case a capturing reader has reached its limit and is then {@link CapturingReader#reset()} to before its limit, it will not
         * call its callback again.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder onLimitReached(Consumer<? super CapturingReader> callback) {
            limitReachedCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Sets a callback that will be triggered when an {@link IOException} occurs while using built capturing readers.
         * A capturing reader can trigger its error callback multiple times.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder onError(BiConsumer<? super CapturingReader, ? super IOException> callback) {
            errorCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Creates a new {@link Config capturing reader configuration} with the settings from this builder.
         *
         * @return The created capturing reader configuration.
         */
        public Config build() {
            return new Config(this);
        }
    }
}
