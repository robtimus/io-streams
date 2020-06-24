/*
 * CharArraySequenceTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharArraySequenceTest extends TestBase {

    @Test
    @DisplayName("reset(char[], int, int)")
    void testReset() {
        char[] array = SOURCE.toCharArray();
        CharArraySequence sequence = new CharArraySequence();

        sequence.reset(array);
        assertEquals(SOURCE.length(), sequence.length());
        assertEquals(SOURCE, sequence.toString());

        assertThrows(NullPointerException.class, () -> sequence.reset(null));
    }

    @Test
    @DisplayName("resetWithStartAndEnd(char[], int, int)")
    void testResetWithStartAndEnd() {
        char[] array = SOURCE.toCharArray();
        CharArraySequence sequence = new CharArraySequence();

        sequence.resetWithStartAndEnd(array, 2, 9);
        assertEquals(7, sequence.length());
        assertEquals(SOURCE.substring(2, 9), sequence.toString());

        assertThrows(NullPointerException.class, () -> sequence.resetWithStartAndEnd(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithStartAndEnd(array, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithStartAndEnd(array, 0, SOURCE.length() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithStartAndEnd(array, SOURCE.length() + 1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithStartAndEnd(array, 4, 3));
    }

    @Test
    @DisplayName("resetWithOffsetAndLength(char[], int, int)")
    void testResetWithOffsetAndLength() {
        char[] array = SOURCE.toCharArray();
        CharArraySequence sequence = new CharArraySequence();

        sequence.resetWithOffsetAndLength(array, 2, 9);
        assertEquals(9, sequence.length());
        assertEquals(SOURCE.substring(2, 11), sequence.toString());

        assertThrows(NullPointerException.class, () -> sequence.resetWithOffsetAndLength(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithOffsetAndLength(array, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithOffsetAndLength(array, 0, SOURCE.length() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithOffsetAndLength(array, SOURCE.length() + 1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.resetWithOffsetAndLength(array, 1, SOURCE.length()));
    }

    @Test
    @DisplayName("charAt(int) and length()")
    void testCharAtAndLength() {
        CharArraySequence sequence = new CharArraySequence();
        sequence.reset(SOURCE.toCharArray());

        testCharAtAndLength(SOURCE, sequence);
    }

    private void testCharAtAndLength(String string, CharArraySequence sequence) {
        assertEquals(string.length(), sequence.length());
        for (int i = 0; i < sequence.length(); i++) {
            assertEquals(string.charAt(i), sequence.charAt(i));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> sequence.charAt(sequence.length()));
    }

    @Test
    @DisplayName("toString()")
    void testToString() {
        CharArraySequence sequence = new CharArraySequence();
        sequence.reset(SOURCE.toCharArray());

        assertEquals(SOURCE, sequence.toString());
        // check caching
        assertSame(sequence.toString(), sequence.toString());
    }

    @Nested
    @DisplayName("subSequence(int, int)")
    class SubSequence {

        @Test
        @DisplayName("invalid indexes")
        void testInvalidIndexes() {
            CharArraySequence sequence = new CharArraySequence();
            sequence.reset(SOURCE.toCharArray());

            assertThrows(IndexOutOfBoundsException.class, () -> sequence.subSequence(-1, SOURCE.length()));
            assertThrows(IndexOutOfBoundsException.class, () -> sequence.subSequence(0, SOURCE.length() + 1));
            assertThrows(IndexOutOfBoundsException.class, () -> sequence.subSequence(1, 0));
        }

        @Test
        @DisplayName("charAt(int) and length()")
        void testCharAtAndLength() {
            testCharAtAndLength(SOURCE, 0, SOURCE.length());
            testCharAtAndLength(SOURCE, 3, SOURCE.length());
            testCharAtAndLength(SOURCE, 0, 8);
            testCharAtAndLength(SOURCE, 3, 8);
        }

        private void testCharAtAndLength(String source, int start, int end) {
            String string = source.substring(start, end);
            CharArraySequence sequence = new CharArraySequence();
            sequence.reset(source.toCharArray());
            sequence = sequence.subSequence(start, end);

            CharArraySequenceTest.this.testCharAtAndLength(string, sequence);
        }

        @Test
        @DisplayName("toString()")
        void testToString() {
            String string = SOURCE.substring(3, 8);
            CharArraySequence sequence = new CharArraySequence();
            sequence.reset(SOURCE.toCharArray());
            sequence = sequence.subSequence(3, 8);

            assertEquals(string, sequence.toString());
        }

        @Nested
        @DisplayName("subSequence(int, int)")
        class SubSubSequence {

            @Test
            @DisplayName("charAt(int) and length()")
            void testCharAtAndLength() {
                testCharAtAndLength(SOURCE, 0, String::length);
                testCharAtAndLength(SOURCE, 1, String::length);
                testCharAtAndLength(SOURCE, 0, s -> 4);
                testCharAtAndLength(SOURCE, 1, s -> 4);
            }

            private void testCharAtAndLength(String source, int start, ToIntFunction<String> endMapper) {
                String substring = source.substring(3, 8);
                int end = endMapper.applyAsInt(substring);
                String string = substring.substring(start, end);
                CharArraySequence sequence = new CharArraySequence();
                sequence.reset(source.toCharArray());
                sequence = sequence.subSequence(3, 8).subSequence(start, end);

                CharArraySequenceTest.this.testCharAtAndLength(string, sequence);
            }

            @Test
            @DisplayName("toString()")
            void testToString() {
                String string = SOURCE.substring(3, 8).substring(1, 4);
                CharArraySequence sequence = new CharArraySequence();
                sequence.reset(SOURCE.toCharArray());
                sequence = sequence.subSequence(3, 8).subSequence(1, 4);

                assertEquals(string, sequence.toString());
            }
        }
    }
}
