/*
 * StreamUtilsTest.java
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
import static com.github.robtimus.io.stream.StreamUtils.checkStartAndEnd;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamUtilsTest extends TestBase {

    @Test
    @DisplayName("checkOffsetAndLength(byte[], int, int)")
    void testCheckOffsetAndLengthForByteArray() {
        byte[] array = SOURCE.getBytes();
        checkOffsetAndLength(array, 0, array.length);
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 0, array.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, array.length));
        checkOffsetAndLength(array, 1, 0);
    }

    @Test
    @DisplayName("checkOffsetAndLength(char[], int, int)")
    void testCheckOffsetAndLengthForCharArray() {
        char[] array = SOURCE.toCharArray();
        checkOffsetAndLength(array, 0, array.length);
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 0, array.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(array, 1, array.length));
        checkOffsetAndLength(array, 1, 0);
    }

    @Test
    @DisplayName("checkOffsetAndLength(CharSequence, int, int)")
    void testCheckOffsetAndLengthForCharSequence() {
        CharSequence sequence = SOURCE;
        checkOffsetAndLength(sequence, 0, sequence.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, -1, sequence.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 0, sequence.length() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkOffsetAndLength(sequence, 1, sequence.length()));
        checkOffsetAndLength(sequence, 1, 0);
    }

    @Test
    @DisplayName("checkStartAndEnd(CharSequence, int, int)")
    void testCheckStartAndEndForCharSequence() {
        checkStartAndEnd(SOURCE, 0, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 0, SOURCE.length() + 1));
        checkStartAndEnd(SOURCE, 1, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, 0));
    }
}
