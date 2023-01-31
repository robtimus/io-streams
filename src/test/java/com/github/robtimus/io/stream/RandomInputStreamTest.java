/*
 * RandomInputStreamTest.java
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

import static com.github.robtimus.io.stream.RandomInputStream.usingAllBytes;
import static com.github.robtimus.io.stream.RandomInputStream.usingGenerator;
import static com.github.robtimus.io.stream.RandomInputStream.usingRange;
import static com.github.robtimus.io.stream.RandomInputStream.usingRangeFrom;
import static com.github.robtimus.io.stream.RandomInputStream.usingRangeUntil;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.RandomInputStream.Builder;

@SuppressWarnings("nls")
class RandomInputStreamTest {

    @Test
    @DisplayName("with proper random")
    void testWithProperRandom() {
        try (RandomInputStream input = usingAllBytes().withLimit(50).build()) {
            int c;
            while ((c = input.read()) != -1) {
                assertThat(c, both(greaterThanOrEqualTo(0)).and(lessThanOrEqualTo(255)));
            }
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Nested
        @DisplayName("withRandomLimit(int, int)")
        class WithRandomLimit {

            @Test
            @DisplayName("negative min")
            void testNegativeMin() {
                Builder builder = usingAllBytes();
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withRandomLimit(-1, 10));
                assertEquals("-1 < 0", exception.getMessage());
            }

            @Test
            @DisplayName("max not larger than min")
            void testMaxNotLargerThanMin() {
                Builder builder = usingAllBytes();
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withRandomLimit(10, 10));
                assertEquals("10 <= 10", exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("limited")
    class Limited {

        @Nested
        @DisplayName("usingAllBytes()")
        class UsingAllBytes extends LimitedBaseTest {

            UsingAllBytes() {
                super(() -> usingAllBytes(), createBytes(MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }

        @Nested
        @DisplayName("usingRange(int, int)")
        class UsingRange extends LimitedBaseTest {

            UsingRange() {
                super(() -> usingRange(100, -1), repeatBytes(100, 255, MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("start > end")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingRange(-1, 0));
                assertEquals("255 > 0", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingRangeFrom(int)")
        class UsingRangeFrom extends LimitedBaseTest {

            UsingRangeFrom() {
                super(() -> usingRangeFrom(100), repeatBytes(100, 255, MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }

        @Nested
        @DisplayName("usingRangeUntil(int)")
        class UsingRangeUntil extends LimitedBaseTest {

            UsingRangeUntil() {
                super(() -> usingRangeUntil(100), repeatBytes(0, 100, MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }

        @Test
        @DisplayName("random limit")
        void testRandomLimit() {
            // no need to test everything again, just the initialization; use read(char[]) for that

            final char first = 32;
            final int limit = 10 + first % 10;
            byte[] expected = new byte[limit];
            for (int i = 0, j = 1; i < limit; i++, j++) {
                expected[i] = (byte) (first + j);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(limit);
            try (RandomInputStream input = usingGenerator(Random::nextInt).withRandomLimit(10, 20).withRandom(new DummyRandom(first)).build()) {
                byte[] buffer = new byte[limit];
                assertEquals(buffer.length, input.read(buffer));
                baos.write(buffer, 0, buffer.length);
                assertEquals(-1, input.read());
            }
            assertArrayEquals(expected, baos.toByteArray());
        }
    }

    @Nested
    @DisplayName("unlimited")
    class Unlimited {

        @Nested
        @DisplayName("usingAllBytes()")
        class UsingAllBytes extends UnlimitedBaseTest {

            UsingAllBytes() {
                super(() -> usingAllBytes(), createBytes(MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }

        @Nested
        @DisplayName("usingRange(int, int)")
        class UsingRange extends UnlimitedBaseTest {

            UsingRange() {
                super(() -> usingRange(100, -1), repeatBytes(100, 255, MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("start > end")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingRange(-1, 0));
                assertEquals("255 > 0", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingRangeFrom(int)")
        class UsingRangeFrom extends UnlimitedBaseTest {

            UsingRangeFrom() {
                super(() -> usingRangeFrom(100), repeatBytes(100, 255, MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }

        @Nested
        @DisplayName("usingRangeUntil(int)")
        class UsingRangeUntil extends UnlimitedBaseTest {

            UsingRangeUntil() {
                super(() -> usingRangeUntil(100), repeatBytes(0, 100, MIN_EXPECTED_SEQUENCE_LENGTH));
            }
        }
    }

    private static byte[] repeatBytes(byte[] b, int minSize) {
        if (b.length >= minSize) {
            return b;
        }
        byte[] sequence = new byte[minSize];
        for (int i = 0, r = minSize; i < minSize; i += b.length, r -= b.length) {
            System.arraycopy(b, 0, sequence, i, Math.min(b.length, r));
        }
        return sequence;
    }

    private static byte[] repeatBytes(int start, int end, int minSize) {
        byte[] bytes = new byte[end - start + 1];
        for (int i = 0, j = start; j <= end; i++, j++) {
            bytes[i] = (byte) j;
        }
        return repeatBytes(bytes, minSize);
    }

    private static byte[] createBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static void readAssertAndAppend(RandomInputStream input, byte[] buffer, ByteArrayOutputStream baos) {
        int n = input.read(buffer);
        assertEquals(buffer.length, n);
        baos.write(buffer, 0, n);
    }

    private static void assertSequence(byte[] expectedSequence, int limit, ByteArrayOutputStream baos) {
        byte[] expectedSubSequence = Arrays.copyOfRange(expectedSequence, 0, limit);
        assertArrayEquals(expectedSubSequence, baos.toByteArray());
    }

    abstract static class LimitedBaseTest {

        private static final int LIMIT = 50;
        static final int MIN_EXPECTED_SEQUENCE_LENGTH = LIMIT * 2;

        private final Supplier<Builder> builderSupplier;
        private final byte[] expectedSequence;

        private LimitedBaseTest(Supplier<Builder> builderSupplier, byte[] expectedSequence) {
            this.builderSupplier = builderSupplier;
            this.expectedSequence = expectedSequence;

            assertTrue(expectedSequence.length >= MIN_EXPECTED_SEQUENCE_LENGTH,
                    "expectedSequence.length should be at least " + MIN_EXPECTED_SEQUENCE_LENGTH);
        }

        private RandomInputStream createInputStream() {
            return builderSupplier.get().transform(this::applyDefaults).build();
        }

        private Builder applyDefaults(Builder builder) {
            return builder.withLimit(LIMIT).withRandom(new DummyRandom());
        }

        @Test
        @DisplayName("read()")
        void testReadByte() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                int b;
                while ((b = input.read()) != -1) {
                    baos.write(b);
                }
            }
            assertSequence(expectedSequence, LIMIT, baos);
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArray() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                byte[] buffer = new byte[10];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            assertSequence(expectedSequence, LIMIT, baos);
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                byte[] buffer = new byte[10];

                readAssertAndAppend(input, buffer, baos);

                assertEquals(10, input.skip(10));

                readAssertAndAppend(input, buffer, baos);

                assertEquals(0, input.skip(0));

                assertEquals(0, input.skip(-1));

                readAssertAndAppend(input, buffer, baos);

                assertEquals(LIMIT - 40, input.skip(Long.MAX_VALUE));

                assertEquals(-1, input.read(buffer));

                assertEquals(0, input.skip(10));
            }
            // read 10, skipped 10, read 10, skipped 0, read 10, skipped the rest
            // the 10 skipped are not generated though
            assertSequence(expectedSequence, 30, baos);
        }

        @Test
        @DisplayName("available()")
        void testAvailable() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                byte[] buffer = new byte[10];
                int len;
                assertEquals(LIMIT, input.available());
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                    assertEquals(LIMIT - baos.size(), input.available());
                }
                assertEquals(0, input.available());
            }
            assertSequence(expectedSequence, LIMIT, baos);
        }

        @Test
        @DisplayName("mark(int) and reset()")
        void testMarkAndReset() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                assertTrue(input.markSupported());

                byte[] buffer = new byte[10];

                readAssertAndAppend(input, buffer, baos);

                input.reset();

                readAssertAndAppend(input, buffer, baos);

                input.mark(0);

                readAssertAndAppend(input, buffer, baos);

                input.reset();

                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            // read 10, reset; read 10; mark; read 10; reset; read remainder
            // two reads of 10 were reset, so add 20 to the limit
            assertSequence(expectedSequence, LIMIT + 20, baos);
        }

        @Test
        @DisplayName("close()")
        void testClose() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createInputStream()) {
                byte[] buffer = new byte[10];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }

                input.close();
                while ((len = input.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            // the limit has been reached twice - once before close, once after
            assertSequence(expectedSequence, LIMIT * 2, baos);
        }
    }

    abstract static class UnlimitedBaseTest {

        private static final int LIMIT = 50;
        static final int MIN_EXPECTED_SEQUENCE_LENGTH = LIMIT * 2;

        private final Supplier<Builder> builderSupplier;
        private final byte[] expectedSequence;

        private UnlimitedBaseTest(Supplier<Builder> builderSupplier, byte[] expectedSequence) {
            this.builderSupplier = builderSupplier;
            this.expectedSequence = expectedSequence;

            assertTrue(expectedSequence.length >= MIN_EXPECTED_SEQUENCE_LENGTH,
                    "expectedSequence.length should be at least " + MIN_EXPECTED_SEQUENCE_LENGTH);
        }

        private RandomInputStream createinput() {
            RandomInputStream input = builderSupplier.get().transform(this::applyDefaults).build();
            // skip forward so most methods will trigger overflow in the index
            input.skip(Long.MAX_VALUE - LIMIT / 2);
            return input;
        }

        private Builder applyDefaults(Builder builder) {
            return builder.withRandom(new DummyRandom());
        }

        @Test
        @DisplayName("read()")
        void testReadByte() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                for (int i = 0; i < MIN_EXPECTED_SEQUENCE_LENGTH; i++) {
                    int b = input.read();
                    assertNotEquals(-1, b);
                    baos.write(b);
                }
                assertNotEquals(-1, input.read());
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, baos.size());
            }
            assertSequence(expectedSequence, MIN_EXPECTED_SEQUENCE_LENGTH, baos);
        }

        @Test
        @DisplayName("read(byte[], int, int)")
        void testReadByteArray() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                byte[] buffer = new byte[10];
                while (baos.size() < MIN_EXPECTED_SEQUENCE_LENGTH) {
                    readAssertAndAppend(input, buffer, baos);
                }
                assertEquals(buffer.length, input.read(buffer));
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, baos.size());
            }
            assertSequence(expectedSequence, MIN_EXPECTED_SEQUENCE_LENGTH, baos);
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                byte[] buffer = new byte[10];

                readAssertAndAppend(input, buffer, baos);

                assertEquals(10, input.skip(10));

                readAssertAndAppend(input, buffer, baos);

                assertEquals(0, input.skip(0));

                assertEquals(0, input.skip(-1));

                readAssertAndAppend(input, buffer, baos);

                assertEquals(Long.MAX_VALUE, input.skip(Long.MAX_VALUE));

                readAssertAndAppend(input, buffer, baos);

                assertEquals(10, input.skip(10));
            }
            // read 10, skipped 10, read 10, skipped 0, read 10, skipped Long.MAX_VALUE, read 10, skipped 10
            // the skipped are not generated though
            assertSequence(expectedSequence, 40, baos);
        }

        @Test
        @DisplayName("available()")
        void testAvailable() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                byte[] buffer = new byte[10];
                assertEquals(Integer.MAX_VALUE, input.available());
                while (baos.size() < MIN_EXPECTED_SEQUENCE_LENGTH) {
                    readAssertAndAppend(input, buffer, baos);
                }
                assertEquals(Integer.MAX_VALUE, input.available());
                assertEquals(buffer.length, input.read(buffer));
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, baos.size());
            }
            assertSequence(expectedSequence, MIN_EXPECTED_SEQUENCE_LENGTH, baos);
        }

        @Test
        @DisplayName("mark(int) and reset()")
        void testMarkAndReset() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                assertTrue(input.markSupported());

                byte[] buffer = new byte[10];

                readAssertAndAppend(input, buffer, baos);

                input.reset();

                readAssertAndAppend(input, buffer, baos);

                input.mark(0);

                readAssertAndAppend(input, buffer, baos);

                input.reset();

                while (baos.size() < MIN_EXPECTED_SEQUENCE_LENGTH - 10) {
                    readAssertAndAppend(input, buffer, baos);
                }
                readAssertAndAppend(input, buffer, baos);
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, baos.size());
            }
            // read 10, reset; read 10; mark; read 10; reset; read MIN_EXPECTED_SEQUENCE_LENGTH
            // two reads of 10 were reset, so add 20 to the limit
            assertSequence(expectedSequence, MIN_EXPECTED_SEQUENCE_LENGTH, baos);
        }

        @Test
        @DisplayName("close()")
        void testClose() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (RandomInputStream input = createinput()) {
                byte[] buffer = new byte[10];
                while (baos.size() < LIMIT) {
                    readAssertAndAppend(input, buffer, baos);
                }
                readAssertAndAppend(input, buffer, baos);

                input.close();

                while (baos.size() < MIN_EXPECTED_SEQUENCE_LENGTH - 10) {
                    readAssertAndAppend(input, buffer, baos);
                }
                readAssertAndAppend(input, buffer, baos);

                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, baos.size());
            }
            assertSequence(expectedSequence, MIN_EXPECTED_SEQUENCE_LENGTH, baos);
        }
    }

    @SuppressWarnings("serial")
    private static final class DummyRandom extends Random {

        private int next;

        private DummyRandom() {
            this(0);
        }

        private DummyRandom(int start) {
            this.next = start;
        }

        @Override
        protected int next(int bits) {
            return next++;
        }

        @Override
        public int nextInt(int bound) {
            return next++ % bound;
        }
    }
}
