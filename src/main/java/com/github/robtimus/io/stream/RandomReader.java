/*
 * RandomReader.java
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
import java.io.Reader;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A reader that will return random characters.
 * This reader supports {@link #mark(int)} and {@link #reset()}. It comes with a default mark at the start of the stream, which means that
 * {@link #reset()} can be called at any time.
 *
 * @author Rob Spoor
 */
public final class RandomReader extends Reader {

    private final Random random;
    private final ToIntFunction<Random> characterGenerator;

    private final long limit;
    private long index;
    private long mark;

    private RandomReader(Builder builder) {
        this.random = builder.random != null ? builder.random : new SecureRandom();
        this.characterGenerator = builder.characterGenerator;

        this.limit = builder.limitGenerator != null ? builder.limitGenerator.applyAsLong(random) : builder.limit;
        this.index = 0;
        this.mark = 0;
    }

    @Override
    public int read() {
        if (limit >= 0 && index >= limit) {
            return -1;
        }
        index++;
        return nextChar();
    }

    @Override
    public int read(char[] cbuf) {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(char[] cbuf, int off, int len) {
        checkOffsetAndLength(cbuf, off, len);

        if (limit >= 0 && index >= limit) {
            return -1;
        }
        int n = limit >= 0 ? (int) Math.min(limit - index, len) : len;
        for (int i = off, j = 0; j < n; i++, j++) {
            cbuf[i] = nextChar();
        }
        index += n;
        return n;
    }

    @Override
    public int read(CharBuffer target) {
        Objects.requireNonNull(target);

        if (limit >= 0 && index >= limit) {
            return -1;
        }
        int n = limit >= 0 ? (int) Math.min(limit - index, target.remaining()) : target.remaining();
        for (int i = 0; i < n; i++) {
            target.put(nextChar());
        }
        index += n;
        return n;
    }

    @Override
    public long skip(long n) {
        if (n < 0) {
            throw new IllegalArgumentException(n + " < 0"); //$NON-NLS-1$
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
    public boolean ready() {
        return limit < 0 || index < limit;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readAheadLimit) {
        mark = index;
    }

    @Override
    public void reset() {
        index = mark;
    }

    /**
     * Closes this reader. This will reset the reader to its default settings.
     */
    @Override
    public void close() {
        index = 0;
        mark = 0;
    }

    private char nextChar() {
        return (char) characterGenerator.applyAsInt(random);
    }

    /**
     * Returns a builder for random readers that can return all possible characters.
     *
     * @return A builder for random readers that can return all possible characters.
     */
    public static Builder usingAllCharacters() {
        return usingGenerator(r -> r.nextInt(Character.MAX_VALUE + 1));
    }

    /**
     * Returns a builder for random readers that can return characters from a specific range.
     *
     * @param start The start of the range, inclusive.
     * @param end The end of the range, inclusive.
     * @return A builder for random readers that can return characters from the given range.
     * @throws IllegalArgumentException If {@code start} is larger than {@code end}.
     */
    public static Builder usingRange(char start, char end) {
        if (start > end) {
            throw new IllegalArgumentException(start + " > " + end); //$NON-NLS-1$
        }
        // both start and end are inclusive, so add 1
        return usingGenerator(r -> start + r.nextInt(end - start + 1));
    }

    /**
     * Returns a builder for random readers that can return characters from a specific alphabet.
     *
     * @param alphabet A string representing the alphabet to use.
     * @return A builder for random readers that can return characters from the given alphabet.
     * @throws NullPointerException If the given alphabet is {@code null}.
     * @throws IllegalArgumentException If the given alphabet is empty.
     */
    public static Builder usingAlphabet(String alphabet) {
        Objects.requireNonNull(alphabet);
        if (alphabet.isEmpty()) {
            throw new IllegalArgumentException(Messages.RandomReader.emptyAlphabet());
        }
        return usingGenerator(r -> alphabet.charAt(r.nextInt(alphabet.length())));
    }

    /**
     * Returns a builder for random readers that can return characters from 0-9 and a-z.
     *
     * @return A builder for random readers that can return characters from 0-9 and a-z.
     */
    public static Builder usingHex() {
        return usingHex(false);
    }

    /**
     * Returns a builder for random readers that can return characters from 0-9 and a-z or A-Z.
     *
     * @param upperCase {@code true} to use A-Z, or {@code false} to use a-z.
     * @return A builder for random readers that can return characters from 0-9 and a-z or A-Z.
     */
    public static Builder usingHex(boolean upperCase) {
        return usingGenerator(r -> randomHex(r, upperCase));
    }

    private static char randomHex(Random r, boolean upperCase) {
        char c = Character.forDigit(r.nextInt(16), 16);
        return upperCase ? Character.toUpperCase(c) : Character.toLowerCase(c);
    }

    /**
     * Returns a builder for random readers that can return digits.
     *
     * @return A builder for random readers that can return digits.
     */
    public static Builder usingDigits() {
        return usingGenerator(r -> '0' + r.nextInt(10));
    }

    /**
     * Returns a builder for random readers that uses a custom generator for characters.
     * For each generated value, only the 16 low-order bits are used; the 16 high-order bits are ignored.
     *
     * @param generator The generator to use.
     * @return A builder for random readers that uses the given custom generator.
     * @throws NullPointerException If the given custom generator is {@code null}.
     */
    public static Builder usingGenerator(ToIntFunction<Random> generator) {
        Objects.requireNonNull(generator);
        return new Builder(generator);
    }

    /**
     * A builder for {@link RandomReader random readers}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final ToIntFunction<Random> characterGenerator;

        private Random random;

        private long limit = -1;
        private ToLongFunction<Random> limitGenerator = null;

        private Builder(ToIntFunction<Random> characterGenerator) {
            this.characterGenerator = characterGenerator;
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
         * <p>
         * Calling this method will discard any changes made by {@link #withRandomLimit(int, int)}.
         *
         * @param limit The limit to set.
         * @return This object.
         */
        public Builder withLimit(long limit) {
            this.limit = Math.max(limit, -1);
            this.limitGenerator = null;
            return this;
        }

        /**
         * Specifies that a random number should be used for the maximum number of characters to return while reading. By default there is no limit.
         * <p>
         * Calling this method will cause any changes made by {@link #withLimit(long)} to be ignored.
         *
         * @param min The minimum allowed limit, inclusive.
         * @param max The maximum allowed limit, exclusive.
         * @return This object.
         * @throws IllegalArgumentException If the minimum is negative, or the maximum is not larger than the minimum.
         */
        public Builder withRandomLimit(int min, int max) {
            if (min < 0) {
                throw new IllegalArgumentException(min + " < 0"); //$NON-NLS-1$
            }
            if (max <= min) {
                throw new IllegalArgumentException(max + " <= " + min); //$NON-NLS-1$
            }
            this.limitGenerator = r -> min + r.nextInt(max - min);
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
         * Creates a new {@link RandomReader random reader} with the settings from this builder.
         *
         * @return The created random reader.
         */
        public RandomReader build() {
            return new RandomReader(this);
        }
    }
}
