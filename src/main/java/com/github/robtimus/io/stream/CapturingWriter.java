/*
 * CapturingWriter.java
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
import java.io.Writer;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.commons.io.output.ProxyCollectionWriter;
import org.apache.commons.io.output.TeeWriter;

/**
 * A writer that captures the content it writes.
 * This is a simplified version of {@link TeeWriter} and {@link ProxyCollectionWriter}.
 *
 * @author Rob Spoor
 */
public final class CapturingWriter extends Writer {

    private final Writer delegate;

    private final StringBuilder captor;
    private final int limit;

    private long totalChars = 0;

    private boolean closed = false;

    private Consumer<CapturingWriter> doneCallback;
    private Consumer<CapturingWriter> limitReachedCallback;

    /**
     * Creates a new capturing writer.
     *
     * @param output The writer to capture from.
     * @param config The configuration to use.
     * @throws NullPointerException If the given writer or config is {@code null}.
     */
    public CapturingWriter(Writer output, Config config) {
        this.delegate = Objects.requireNonNull(output);

        captor = config.expectedCount < 0 ? new StringBuilder() : new StringBuilder(Math.min(config.expectedCount, config.limit));
        limit = config.limit;

        doneCallback = config.doneCallback;
        limitReachedCallback = config.limitReachedCallback;
    }

    @Override
    public void write(int c) throws IOException {
        delegate.write(c);

        totalChars++;
        if (captor.length() < limit) {
            captor.append((char) c);
            checkLimitReached();
        }
    }

    @Override
    public void write(char[] c) throws IOException {
        delegate.write(c);

        totalChars += c.length;

        int allowed = Math.min(limit - captor.length(), c.length);
        if (allowed > 0) {
            captor.append(c, 0, allowed);
            checkLimitReached();
        }
    }

    @Override
    public void write(char[] c, int off, int len) throws IOException {
        delegate.write(c, off, len);

        totalChars += len;

        int allowed = Math.min(limit - captor.length(), len);
        if (allowed > 0) {
            captor.append(c, off, allowed);
            checkLimitReached();
        }
    }

    @Override
    public void write(String str) throws IOException {
        delegate.write(str);

        totalChars += str.length();

        int allowed = Math.min(limit - captor.length(), str.length());
        if (allowed > 0) {
            captor.append(str, 0, allowed);
            checkLimitReached();
        }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        delegate.write(str, off, len);

        totalChars += len;

        int allowed = Math.min(limit - captor.length(), len);
        if (allowed > 0) {
            captor.append(str, off, off + allowed);
            checkLimitReached();
        }
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        delegate.append(csq);

        CharSequence cs = csq != null ? csq : "null"; //$NON-NLS-1$

        totalChars += cs.length();

        int allowed = Math.min(limit - captor.length(), cs.length());
        if (allowed > 0) {
            captor.append(csq, 0, allowed);
            checkLimitReached();
        }

        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        delegate.append(csq, start, end);

        totalChars += end - start;

        int allowed = Math.min(limit - captor.length(), end - start);
        if (allowed > 0) {
            captor.append(csq, start, start + allowed);
            checkLimitReached();
        }

        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        delegate.write(c);

        totalChars++;
        if (captor.length() < limit) {
            captor.append(c);
            checkLimitReached();
        }

        return this;
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
        if (totalChars >= limit && limitReachedCallback != null) {
            limitReachedCallback.accept(this);
            limitReachedCallback = null;
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
     * Returns the total number of characters that have been written.
     *
     * @return The total number of characters that have been written.
     */
    public long totalChars() {
        return totalChars;
    }

    /**
     * Returns whether or not this writer has been closed.
     *
     * @return {@code true} if this writer has been closed, or {@code false} otherwise.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Creates a builder for capturing writer configurations.
     *
     * @return The created builder.
     */
    public static Builder config() {
        return new Builder();
    }

    /**
     * Configuration for {@link CapturingWriter capturing writers}.
     *
     * @author Rob Spoor
     */
    public static final class Config {

        private final int limit;

        private final int expectedCount;

        private final Consumer<CapturingWriter> doneCallback;
        private final Consumer<CapturingWriter> limitReachedCallback;

        private Config(Builder builder) {
            this.limit = builder.limit;

            this.expectedCount = builder.expectedCount;

            this.doneCallback = builder.doneCallback;
            this.limitReachedCallback = builder.limitReachedCallback;
        }
    }

    /**
     * A builder for {@link Config capturing writer configurations}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private int limit = Integer.MAX_VALUE;

        private int expectedCount = -1;

        private Consumer<CapturingWriter> doneCallback;
        private Consumer<CapturingWriter> limitReachedCallback;

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
         * Sets the expected number of characters that can be written to the wrapped writer.
         * This can be used for performance reasons; if this is set then the capture buffer will be pre-allocated.
         * The default value is {@code -1}.
         *
         * @param expectedCount The expected number of characters that can be written to the wrapped writer, or a negative number if not known.
         * @return This object.
         */
        public Builder withExpectedCount(int expectedCount) {
            this.expectedCount = expectedCount;
            return this;
        }

        /**
         * Sets a callback that will be triggered when reading from built capturing writers is done. This can be because the writer is
         * {@link CapturingWriter#isClosed() closed} or because it has been explicitly marked as {@link CapturingWriter#done() done}.
         * A capturing writer will only trigger its callback once.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder whenDone(Consumer<CapturingWriter> callback) {
            doneCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Sets a callback that will be triggered when built capturing writers hit their limit. If a writer never reaches its limit its callback will
         * never be called.
         *
         * @param callback The callback to set.
         * @return This object.
         * @throws NullPointerException If the given callback is {@code null}.
         */
        public Builder whenLimitReached(Consumer<CapturingWriter> callback) {
            limitReachedCallback = Objects.requireNonNull(callback);
            return this;
        }

        /**
         * Creates a new {@link Config capturing writer configuration} with the settings from this builder.
         *
         * @return The created capturing writer configuration.
         */
        public Config build() {
            return new Config(this);
        }
    }
}
