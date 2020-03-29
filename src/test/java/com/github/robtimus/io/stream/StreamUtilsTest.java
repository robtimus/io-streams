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
import static com.github.robtimus.io.stream.StreamUtils.writer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@SuppressWarnings({ "javadoc", "nls" })
public class StreamUtilsTest extends TestBase {

    @TestFactory
    @DisplayName("writer(Appendable)")
    public DynamicTest[] testWriter() {
        return new DynamicTest[] {
                dynamicTest("Writer", () -> {
                    Writer writer = new StringWriter();
                    @SuppressWarnings("resource")
                    Writer writer2 = writer(writer);
                    assertSame(writer, writer2);
                }),
                dynamicTest("StringBuilder", () -> {
                    StringBuilder sb = new StringBuilder();
                    @SuppressWarnings("resource")
                    Writer writer = writer(sb);
                    assertThat(writer, instanceOf(AppendableWriter.class));
                }),
                dynamicTest("null", () -> {
                    assertThrows(NullPointerException.class, () -> writer(null));
                }),
        };
    }

    @Test
    @DisplayName("checkOffsetAndLength(byte[], int, int)")
    public void testCheckOffsetAndLengthForByteArray() {
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
    public void testCheckOffsetAndLengthForCharArray() {
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
    public void testCheckOffsetAndLengthForCharSequence() {
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
    public void testCheckStartAndEndForCharSequence() {
        checkStartAndEnd(SOURCE, 0, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, -1, SOURCE.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 0, SOURCE.length() + 1));
        checkStartAndEnd(SOURCE, 1, SOURCE.length());
        assertThrows(IndexOutOfBoundsException.class, () -> checkStartAndEnd(SOURCE, 1, 0));
    }
}
