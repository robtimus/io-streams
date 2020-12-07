/*
 * CapturingOutputStream.java
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
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.commons.io.output.TeeOutputStream;

/**
 * An output stream that captures the content it writes.
 * This is a simplified version of {@link TeeOutputStream}.
 *
 * @author Rob Spoor
 */
public final class CapturingOutputStream extends OutputStream {

    private final OutputStream delegate;

    private final ByteCaptor captor;
    private final int limit;

    private long totalBytes = 0;

    private boolean closed = false;

    private Consumer<CapturingOutputStream> doneCallback;
    private Consumer<CapturingOutputStream> limitReachedCallback;

    /**
     * Creates a new capturing output stream.
     *
     * @param output The output stream to capture from.
     * @param config The configuration to use.
     * @throws NullPointerException If the given output stream or config is {@code null}.
     */
    public CapturingOutputStream(OutputStream output, Config config) {
        this.delegate = Objects.requireNonNull(output);

        captor = config.expectedCount < 0 ? new ByteCaptor() : new ByteCaptor(Math.min(config.expectedCount, config.limit));
        limit = config.limit;

        doneCallback = config.doneCallback;
        limitReachedCallback = config.limitReachedCallback;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);

        totalBytes++;
        if (captor.size() < limit) {
            captor.write(b);
            checkLimitReached();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);

        totalBytes += b.length;

        int allowed = Math.min(limit - captor.size(), b.length);
        if (allowed > 0) {
            captor.write(b, 0, allowed);
            checkLimitReached();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);

        totalBytes += len;

        int allowed = Math.min(limit - captor.size(), len);
        if (allowed > 0) {
            captor.write(b, off, allowed);
            checkLimitReached();
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        markAsClosed();
    }

    /**
     * Marks the capturing as done. This method
     */
    public void done() {
        if (doneCallback != null) {
            doneCallback.accept(this);
            doneCallback = null;
        }
    }

    private void markAsClosed() {
        closed = true;
        done();
    }

    private void checkLimitReached() {
        if (totalBytes >= limit && limitReachedCallback != null) {
            limitReachedCallback.accept(this);
            limitReachedCallback = null;
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
     * Returns the total number of bytes that have been written.
     *
     * @return The total number of bytes that have been written.
     */
    public long totalBytes() {
        return totalBytes;
    }

    /**
     * Returns whether or not this output stream has been closed.
     *
     * @return {@code true} if this output stream has been closed, or {@code false} otherwise.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Creates a builder for capturing output stream configurations.
     *
     * @return The created builder.
     */
    public static Builder config() {
        return new Builder();
    }

    /**
     * Configuration for {@link CapturingOutputStream capturing output streams}.
     *
     * @author Rob Spoor
     */
    public static final class Config {

        private final int limit;

        private final int expectedCount;

        private final Consumer<CapturingOutputStream> doneCallback;
        private final Consumer<CapturingOutputStream> limitReachedCallback;

        private Config(Builder builder) {
            this.limit = builder.limit;

            this.expectedCount = builder.expectedCount;

            this.doneCallback = builder.doneCallback;
            this.limitReachedCallback = builder.limitReachedCallback;
        }
    }

    /**
     * A builder for {@link Config capturing output stream configurations}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private int limit = Integer.MAX_VALUE;

        private int expectedCount = -1;

        private Consumer<CapturingOutputStream> doneCallback;
        private Consumer<CapturingOutputStream> limitReachedCallback;

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
         * Sets the expected number of bytes that can be written to the wrapped output stream.
         * This can be used for performance reasons; if this is set then the capture buffer will be pre-allocated.
         * The default value is {@code -1}.
         *
         * @param expectedCount The expected number of bytes that can be written to the wrapped output stream, or a negative number if not known.
         * @return This object.
         */
        public Builder withExpectedCount(int expectedCount) {
            this.expectedCount = expectedCount;
            return this;
        }

        /**
         * Sets a callback that will be triggered when reading from built capturing output streams is done. This can be because the output stream is
         * {@link CapturingOutputStream#isClosed() closed} or because it has been explicitly marked as {@link CapturingOutputStream#done() done}.
         * A capturing output stream will only trigger its callback once.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder onDone(Consumer<CapturingOutputStream> callback) {
            doneCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Sets a callback that will be triggered when built capturing output streams hit their limit. If an output stream never reaches its limit its
         * callback will never be called.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder onLimitReached(Consumer<CapturingOutputStream> callback) {
            limitReachedCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Creates a new {@link Config capturing output stream configuration} with the settings from this builder.
         *
         * @return The created capturing output stream configuration.
         */
        public Config build() {
            return new Config(this);
        }
    }
}
