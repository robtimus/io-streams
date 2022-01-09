/*
 * RandomReaderTest.java
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

import static com.github.robtimus.io.stream.RandomReader.usingAllCharacters;
import static com.github.robtimus.io.stream.RandomReader.usingAlphabet;
import static com.github.robtimus.io.stream.RandomReader.usingDigits;
import static com.github.robtimus.io.stream.RandomReader.usingGenerator;
import static com.github.robtimus.io.stream.RandomReader.usingHex;
import static com.github.robtimus.io.stream.RandomReader.usingRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.CharBuffer;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.io.stream.RandomReader.Builder;

@SuppressWarnings("nls")
class RandomReaderTest {

    @Test
    @DisplayName("with proper random")
    void testWithProperRandom() {
        try (RandomReader reader = usingDigits().withLimit(50).build()) {
            int c;
            while ((c = reader.read()) != -1) {
                assertThat(c, both(greaterThanOrEqualTo((int) '0')).and(lessThanOrEqualTo((int) '9')));
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
                Builder builder = usingDigits();
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withRandomLimit(-1, 10));
                assertEquals("-1 < 0", exception.getMessage());
            }

            @Test
            @DisplayName("max not larger than min")
            void testMaxNotLargerThanMin() {
                Builder builder = usingDigits();
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withRandomLimit(10, 10));
                assertEquals("10 <= 10", exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("limited")
    class Limited {

        @Nested
        @DisplayName("usingAllCharacters()")
        class UsingAllCharacters extends LimitedBaseTest {

            UsingAllCharacters() {
                super(() -> usingAllCharacters(), createString(MIN_EXPECTED_SEQUENCE_LENGTH, '!'), '!');
            }
        }

        @Nested
        @DisplayName("usingRange(char, char)")
        class UsingRange extends LimitedBaseTest {

            UsingRange() {
                super(() -> usingRange('a', 'h'), repeatString("abcdefgh", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("start > end")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingRange('b', 'a'));
                assertEquals("b > a", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingAlphabet(String)")
        class UsingAlphabet extends LimitedBaseTest {

            UsingAlphabet() {
                super(() -> usingAlphabet("abcdefghijklm"), repeatString("abcdefghijklm", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("empty alphabet")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingAlphabet(""));
                assertEquals(Messages.RandomReader.emptyAlphabet.get(), exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingHex")
        class UsingHex {

            @Nested
            @DisplayName("usingHex()")
            class DefaultCase extends LimitedBaseTest {

                DefaultCase() {
                    super(() -> usingHex(), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, false));
                }
            }

            @Nested
            @DisplayName("usingHex(false)")
            class LowerCase extends LimitedBaseTest {

                LowerCase() {
                    super(() -> usingHex(false), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, false));
                }
            }

            @Nested
            @DisplayName("usingHex(true)")
            class UpperCase extends LimitedBaseTest {

                UpperCase() {
                    super(() -> usingHex(true), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, true));
                }
            }
        }

        @Nested
        @DisplayName("usingDigits()")
        class UsingDigits extends LimitedBaseTest {

            UsingDigits() {
                super(() -> usingDigits(), repeatString("0123456789", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("empty alphabet")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingAlphabet(""));
                assertEquals(Messages.RandomReader.emptyAlphabet.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("random limit")
        void testRandomLimit() {
            // no need to test everything again, just the initialization; use read(char[]) for that

            final char first = ' ';
            final int limit = 10 + first % 10;
            StringBuilder expected = new StringBuilder(limit);
            for (int i = 0, j = 1; i < limit; i++, j++) {
                expected.append((char) (first + j));
            }

            StringBuilder sb = new StringBuilder(limit);
            try (RandomReader reader = usingGenerator(Random::nextInt).withRandomLimit(10, 20).withRandom(new DummyRandom(first)).build()) {
                char[] buffer = new char[limit];
                assertEquals(buffer.length, reader.read(buffer));
                sb.append(buffer);
                assertEquals(-1, reader.read());
            }
            assertEquals(expected.toString(), sb.toString());
        }
    }

    @Nested
    @DisplayName("unlimited")
    class Unlimited {

        @Nested
        @DisplayName("usingAllCharacters()")
        class UsingAllCharacters extends UnlimitedBaseTest {

            UsingAllCharacters() {
                super(() -> usingAllCharacters(), createString(MIN_EXPECTED_SEQUENCE_LENGTH, '!'), '!');
            }
        }

        @Nested
        @DisplayName("usingRange(char, char)")
        class UsingRange extends UnlimitedBaseTest {

            UsingRange() {
                super(() -> usingRange('a', 'h'), repeatString("abcdefgh", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("start > end")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingRange('b', 'a'));
                assertEquals("b > a", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingAlphabet(String)")
        class UsingAlphabet extends UnlimitedBaseTest {

            UsingAlphabet() {
                super(() -> usingAlphabet("abcdefghijklm"), repeatString("abcdefghijklm", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("empty alphabet")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingAlphabet(""));
                assertEquals(Messages.RandomReader.emptyAlphabet.get(), exception.getMessage());
            }
        }

        @Nested
        @DisplayName("usingHex")
        class UsingHex {

            @Nested
            @DisplayName("usingHex()")
            class DefaultCase extends UnlimitedBaseTest {

                DefaultCase() {
                    super(() -> usingHex(), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, false));
                }
            }

            @Nested
            @DisplayName("usingHex(false)")
            class LowerCase extends UnlimitedBaseTest {

                LowerCase() {
                    super(() -> usingHex(false), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, false));
                }
            }

            @Nested
            @DisplayName("usingHex(true)")
            class UpperCase extends UnlimitedBaseTest {

                UpperCase() {
                    super(() -> usingHex(true), createExpectedHexSequence(MIN_EXPECTED_SEQUENCE_LENGTH, true));
                }
            }
        }

        @Nested
        @DisplayName("usingDigits()")
        class UsingDigits extends UnlimitedBaseTest {

            UsingDigits() {
                super(() -> usingDigits(), repeatString("0123456789", MIN_EXPECTED_SEQUENCE_LENGTH));
            }

            @Test
            @DisplayName("empty alphabet")
            void testStartLargerThanEnd() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> usingAlphabet(""));
                assertEquals(Messages.RandomReader.emptyAlphabet.get(), exception.getMessage());
            }
        }
    }

    private static String repeatString(String s, int minSize) {
        String sequence = s;
        while (sequence.length() < minSize) {
            sequence += sequence;
        }
        return sequence;
    }

    private static String createString(int size, char from) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = (char) (from + i);
        }
        return new String(chars);
    }

    private static String createExpectedHexSequence(int minSize, boolean upperCase) {
        String sequence = "0123456789abcdef";
        if (upperCase) {
            sequence = sequence.toUpperCase();
        }
        return repeatString(sequence, minSize);
    }

    private static void readAssertAndAppend(RandomReader reader, char[] buffer, StringBuilder sb) {
        int n = reader.read(buffer);
        assertEquals(buffer.length, n);
        sb.append(buffer, 0, n);
    }

    abstract static class LimitedBaseTest {

        private static final int LIMIT = 50;
        static final int MIN_EXPECTED_SEQUENCE_LENGTH = LIMIT * 2;

        private final Supplier<Builder> builderSupplier;
        private final String expectedSequence;
        private final int randomStart;

        private LimitedBaseTest(Supplier<Builder> builderSupplier, String expectedSequence) {
            this(builderSupplier, expectedSequence, 0);
        }

        private LimitedBaseTest(Supplier<Builder> builderSupplier, String expectedSequence, int randomStart) {
            this.builderSupplier = builderSupplier;
            this.expectedSequence = expectedSequence;
            this.randomStart = randomStart;

            assertTrue(expectedSequence.length() >= MIN_EXPECTED_SEQUENCE_LENGTH,
                    "expectedSequence.length() should be at least " + MIN_EXPECTED_SEQUENCE_LENGTH);
        }

        private RandomReader createReader() {
            return builderSupplier.get().transform(this::applyDefaults).build();
        }

        private Builder applyDefaults(Builder builder) {
            return builder.withLimit(LIMIT).withRandom(new DummyRandom(randomStart));
        }

        @Test
        @DisplayName("read()")
        void testReadChar() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
            }
            assertEquals(expectedSequence.substring(0, LIMIT), sb.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadCharArray() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
            }
            assertEquals(expectedSequence.substring(0, LIMIT), sb.toString());
        }

        @Test
        @DisplayName("read(CharBuffer)")
        void testReadCharBuffer() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                CharBuffer buffer = CharBuffer.allocate(10);
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    buffer.rewind();
                    sb.append(buffer, 0, len);
                    buffer.clear();
                }
            }
            assertEquals(expectedSequence.substring(0, LIMIT), sb.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(10, reader.skip(10));

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(0, reader.skip(0));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals("-1 < 0", exception.getMessage());

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(LIMIT - 40, reader.skip(Long.MAX_VALUE));

                assertEquals(-1, reader.read(buffer));

                assertEquals(0, reader.skip(10));
            }
            // read 10, skipped 10, read 10, skipped 0, read 10, skipped the rest
            // the 10 skipped are not generated though
            assertEquals(expectedSequence.substring(0, 30), sb.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                int len;
                assertTrue(reader.ready());
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                    assertEquals(sb.length() != LIMIT, reader.ready());
                }
                assertFalse(reader.ready());
            }
            assertEquals(expectedSequence.substring(0, LIMIT), sb.toString());
        }

        @Test
        @DisplayName("mark(int) and reset()")
        void testMarkAndReset() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                assertTrue(reader.markSupported());

                char[] buffer = new char[10];

                readAssertAndAppend(reader, buffer, sb);

                reader.reset();

                readAssertAndAppend(reader, buffer, sb);

                reader.mark(0);

                readAssertAndAppend(reader, buffer, sb);

                reader.reset();

                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
            }
            // read 10, reset; read 10; mark; read 10; reset; read remainder
            // two reads of 10 were reset, so add 20 to the limit
            assertEquals(expectedSequence.substring(0, LIMIT + 20), sb.toString());
        }

        @Test
        @DisplayName("close()")
        void testClose() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }

                reader.close();
                while ((len = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
            }
            // the limit has been reached twice - once before close, once after
            assertEquals(expectedSequence.substring(0, LIMIT * 2), sb.toString());
        }
    }

    abstract static class UnlimitedBaseTest {

        private static final int LIMIT = 50;
        static final int MIN_EXPECTED_SEQUENCE_LENGTH = LIMIT * 2;

        private final Supplier<Builder> builderSupplier;
        private final String expectedSequence;
        private final int randomStart;

        private UnlimitedBaseTest(Supplier<Builder> builderSupplier, String expectedSequence) {
            this(builderSupplier, expectedSequence, 0);
        }

        private UnlimitedBaseTest(Supplier<Builder> builderSupplier, String expectedSequence, int randomStart) {
            this.builderSupplier = builderSupplier;
            this.expectedSequence = expectedSequence;
            this.randomStart = randomStart;

            assertTrue(expectedSequence.length() >= MIN_EXPECTED_SEQUENCE_LENGTH,
                    "expectedSequence.length() should be at least " + MIN_EXPECTED_SEQUENCE_LENGTH);
        }

        private RandomReader createReader() {
            RandomReader reader = builderSupplier.get().transform(this::applyDefaults).build();
            // skip forward so most methods will trigger overflow in the index
            reader.skip(Long.MAX_VALUE - LIMIT / 2);
            return reader;
        }

        private Builder applyDefaults(Builder builder) {
            return builder.withRandom(new DummyRandom(randomStart));
        }

        @Test
        @DisplayName("read()")
        void testReadChar() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                for (int i = 0; i < MIN_EXPECTED_SEQUENCE_LENGTH; i++) {
                    int c = reader.read();
                    assertNotEquals(-1, c);
                    sb.append((char) c);
                }
                assertNotEquals(-1, reader.read());
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }

        @Test
        @DisplayName("read(char[], int, int)")
        void testReadCharArray() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                while (sb.length() < MIN_EXPECTED_SEQUENCE_LENGTH) {
                    readAssertAndAppend(reader, buffer, sb);
                }
                assertEquals(buffer.length, reader.read(buffer));
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }

        @Test
        @DisplayName("read(CharBuffer)")
        void testReadCharBuffer() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                CharBuffer buffer = CharBuffer.allocate(10);
                while (sb.length() < MIN_EXPECTED_SEQUENCE_LENGTH) {
                    int len = reader.read(buffer);
                    assertEquals(buffer.capacity(), len);
                    buffer.rewind();
                    sb.append(buffer, 0, len);
                    buffer.clear();
                }
                assertEquals(buffer.capacity(), reader.read(buffer));
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }

        @Test
        @DisplayName("skip(long)")
        void testSkip() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(10, reader.skip(10));

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(0, reader.skip(0));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
                assertEquals("-1 < 0", exception.getMessage());

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(Long.MAX_VALUE, reader.skip(Long.MAX_VALUE));

                readAssertAndAppend(reader, buffer, sb);

                assertEquals(10, reader.skip(10));
            }
            // read 10, skipped 10, read 10, skipped 0, read 10, skipped Long.MAX_VALUE, read 10, skipped 10
            // the skipped are not generated though
            assertEquals(expectedSequence.substring(0, 40), sb.toString());
        }

        @Test
        @DisplayName("ready()")
        void testReady() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                assertTrue(reader.ready());
                while (sb.length() < MIN_EXPECTED_SEQUENCE_LENGTH) {
                    readAssertAndAppend(reader, buffer, sb);
                }
                assertTrue(reader.ready());
                assertEquals(buffer.length, reader.read(buffer));
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }

        @Test
        @DisplayName("mark(int) and reset()")
        void testMarkAndReset() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                assertTrue(reader.markSupported());

                char[] buffer = new char[10];

                readAssertAndAppend(reader, buffer, sb);

                reader.reset();

                readAssertAndAppend(reader, buffer, sb);

                reader.mark(0);

                readAssertAndAppend(reader, buffer, sb);

                reader.reset();

                while (sb.length() < MIN_EXPECTED_SEQUENCE_LENGTH - 10) {
                    readAssertAndAppend(reader, buffer, sb);
                }
                readAssertAndAppend(reader, buffer, sb);
                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            // read 10, reset; read 10; mark; read 10; reset; read MIN_EXPECTED_SEQUENCE_LENGTH
            // two reads of 10 were reset, so add 20 to the limit
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }

        @Test
        @DisplayName("close()")
        void testClose() {
            StringBuilder sb = new StringBuilder();
            try (RandomReader reader = createReader()) {
                char[] buffer = new char[10];
                while (sb.length() < LIMIT) {
                    readAssertAndAppend(reader, buffer, sb);
                }
                readAssertAndAppend(reader, buffer, sb);

                reader.close();

                while (sb.length() < MIN_EXPECTED_SEQUENCE_LENGTH - 10) {
                    readAssertAndAppend(reader, buffer, sb);
                }
                readAssertAndAppend(reader, buffer, sb);

                assertEquals(MIN_EXPECTED_SEQUENCE_LENGTH, sb.length());
            }
            assertEquals(expectedSequence.substring(0, MIN_EXPECTED_SEQUENCE_LENGTH), sb.toString());
        }
    }

    @SuppressWarnings("serial")
    private static final class DummyRandom extends Random {

        private int next;

        private DummyRandom(int start) {
            next = start;
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
