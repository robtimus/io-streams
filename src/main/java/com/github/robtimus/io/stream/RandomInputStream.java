/*
 * RandomInputStream.java
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

import static com.github.robtimus.io.stream.StreamUtils.checkOffsetAndLength;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * An input stream that will return random bytes.
 * This input stream supports {@link #mark(int)} and {@link #reset()}. It comes with a default mark at the start of the stream, which means that
 * {@link #reset()} can be called at any time.
 *
 * @author Rob Spoor
 */
public final class RandomInputStream extends InputStream {

    private final Random random;
    private final ToIntFunction<Random> byteGenerator;

    private final long limit;
    private long index;
    private long mark;

    private RandomInputStream(Builder builder) {
        this.random = builder.random != null ? builder.random : new SecureRandom();
        this.byteGenerator = builder.byteGenerator;

        this.limit = builder.limit;
        this.index = 0;
        this.mark = 0;
    }

    @Override
    public int read() {
        if (limit >= 0 && index >= limit) {
            return -1;
        }
        index++;
        return nextByte() & 0xFF;
    }

    @Override
    public int read(byte[] cbuf) {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        checkOffsetAndLength(b, off, len);

        if (limit >= 0 && index >= limit) {
            return -1;
        }
        int n = limit >= 0 ? (int) Math.min(limit - index, len) : len;
        for (int i = off, j = 0; j < n; i++, j++) {
            b[i] = nextByte();
        }
        index += n;
        return n;
    }

    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }

        if (limit < 0) {
            index += n;
            return n;
        }

        if (index >= limit) {
            return 0;
        }
        long s = Math.min(limit - index, n);
        index += s;
        return s;
    }

    @Override
    public int available() {
        if (limit < 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0, limit - index);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readAheadLimit) {
        mark = index;
    }

    @Override
    public synchronized void reset() {
        index = mark;
    }

    /**
     * Closes this input stream. This will reset the input stream to its default settings.
     */
    @Override
    public void close() {
        index = 0;
        mark = 0;
    }

    private byte nextByte() {
        return (byte) byteGenerator.applyAsInt(random);
    }

    /**
     * Returns a builder for random input streams that can return all possible bytes.
     *
     * @return A builder for random input streams that can return all possible bytes.
     */
    public static Builder usingAllBytes() {
        return usingGenerator(r -> r.nextInt(Byte.MAX_VALUE + 1));
    }

    /**
     * Returns a builder for random input streams that can return bytes from a specific range.
     * For the start and end of the range, only the 8 low-order bits are used; the 24 high-order bits are ignored.
     * It's therefore advised to use unsigned values between {@code 0} and {@code 255} or {@code 0xFF}.
     *
     * @param start The start of the range, inclusive.
     * @param end The end of the range, inclusive.
     * @return A builder for random input streams that can return bytes from the given range.
     * @throws IllegalArgumentException If {@code start} is larger than {@code end}.
     */
    public static Builder usingRange(int start, int end) {
        int s = start & 0xFF;
        int e = end & 0xFF;
        if (s > e) {
            throw new IllegalArgumentException(s + " > " + e); //$NON-NLS-1$
        }
        // both from and to are inclusive, so add 1
        return usingGenerator(r -> s + r.nextInt(e - s + 1));
    }

    /**
     * Returns a builder for random input streams that can return bytes from a specific range.
     * This method is equal to {@link #usingRange(int, int) usingRange(start, 0xFF)}.
     *
     * @param start The start of the range, inclusive.
     * @return A builder for random input streams that can return bytes from the given range.
     */
    public static Builder usingRangeFrom(int start) {
        return usingRange(start, 0xFF);
    }

    /**
     * Returns a builder for random input streams that can return bytes from a specific range.
     * This method is equal to {@link #usingRange(int, int) usingRange(0, end)}.
     *
     * @param end The end of the range, inclusive.
     * @return A builder for random input streams that can return bytes from the given range.
     */
    public static Builder usingRangeUntil(int end) {
        return usingRange(0, end);
    }

    /**
     * Returns a builder for random input streams that uses a custom generator for characters.
     * For each generated value, only the 8 low-order bits are used; the 24 high-order bits are ignored.
     *
     * @param generator The generator to use.
     * @return A builder for random input streams that uses the given custom generator.
     * @throws NullPointerException If the given custom generator is {@code null}.
     */
    public static Builder usingGenerator(ToIntFunction<Random> generator) {
        Objects.requireNonNull(generator);
        return new Builder(generator);
    }

    /**
     * A builder for {@link RandomInputStream random input streams}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final ToIntFunction<Random> byteGenerator;

        private Random random;

        private long limit = -1;

        private Builder(ToIntFunction<Random> characterGenerator) {
            this.byteGenerator = characterGenerator;
        }

        /**
         * Specifies the random to use. By default a new {@link SecureRandom} will be created.
         *
         * @param random The random to use.
         * @return This object.
         * @throws NullPointerException If the given random is {@code null}.
         */
        public Builder withRandom(Random random) {
            this.random = Objects.requireNonNull(random);
            return this;
        }

        /**
         * Specifies the maximum number of characters to return while reading. By default there is no limit.
         * Use a negative value to specify no limit.
         *
         * @param limit The limit to set.
         * @return This object.
         */
        public Builder withLimit(long limit) {
            this.limit = Math.max(limit, -1);
            return this;
        }

        /**
         * This method allows the application of a function to this builder object.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder object.
         */
        public <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@link RandomInputStream random input stream} with the settings from this builder.
         *
         * @return The created random input stream.
         */
        public RandomInputStream build() {
            return new RandomInputStream(this);
        }
    }
}
