/*
 * MultiLineReaderTest.java
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import com.github.robtimus.io.stream.MultiLineReader.Entry;

@SuppressWarnings("nls")
class MultiLineReaderTest {

    @Test
    @DisplayName("spliterator()")
    void testSpliterator() throws IOException {
        // actual spliterating will be tested with stream

        try (MultiLineReader reader = new MultiLineReader(new StringReader(""), l -> true)) {
            Spliterator<Entry> spliterator = reader.spliterator();
            assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
            assertTrue(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
            assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL));
        }
    }

    @Nested
    @DisplayName("empty input")
    class EmptyInput {

        @Test
        @DisplayName("iterator()")
        void testIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                Iterator<Entry> iterator = reader.iterator();
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
            }
        }

        @Test
        @DisplayName("stream()")
        void testStream() throws IOException {
            try (MultiLineReader reader = createReader()) {
                List<Entry> entries = reader.entries().collect(Collectors.toList());
                assertEquals(Collections.emptyList(), entries);
            }
        }

        private MultiLineReader createReader() {
            return new MultiLineReader(new StringReader(""), l -> true);
        }
    }

    @Nested
    @DisplayName("single-line input")
    class SingleLineInput {

        private List<String> lines = Arrays.asList("Entry1", "Entry2", "Entry3", "Entry4");
        private String content = String.join("\n", lines);

        @Test
        @DisplayName("iterator()")
        void testIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                Iterator<Entry> iterator = reader.iterator();
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Collections.singletonList("Entry1")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Collections.singletonList("Entry2")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Collections.singletonList("Entry3")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Collections.singletonList("Entry4")), iterator.next());
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
            }
        }

        @Test
        @DisplayName("stream()")
        void testStream() throws IOException {
            try (MultiLineReader reader = createReader()) {
                List<Entry> entries = reader.entries().collect(Collectors.toList());
                List<Entry> expected = lines.stream()
                        .map(l -> new Entry(Collections.singletonList(l)))
                        .collect(Collectors.toList());
                assertEquals(expected, entries);
            }
        }

        private MultiLineReader createReader() {
            return new MultiLineReader(new StringReader(content), Pattern.compile("^Entry.*$"));
        }
    }

    @Nested
    @DisplayName("multi-line input")
    class MultiLineInput {

        private List<String> lines = Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2",
                "Entry2", "- Entry2.1", "- Entry2.2",
                "Entry3", "- Entry3.1", "- Entry3.2",
                "Entry4", "- Entry4.1", "- Entry4.2");
        private String content = String.join("\n", lines);

        @Test
        @DisplayName("iterator()")
        void testIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                Iterator<Entry> iterator = reader.iterator();
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Arrays.asList("Entry2", "- Entry2.1", "- Entry2.2")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Arrays.asList("Entry3", "- Entry3.1", "- Entry3.2")), iterator.next());
                assertTrue(iterator.hasNext());
                assertEquals(new Entry(Arrays.asList("Entry4", "- Entry4.1", "- Entry4.2")), iterator.next());
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
            }
        }

        @Test
        @DisplayName("stream()")
        void testStream() throws IOException {
            try (MultiLineReader reader = createReader()) {
                List<Entry> entries = reader.entries().collect(Collectors.toList());
                List<Entry> expected = Arrays.asList(
                        new Entry(lines.subList(0, 3)),
                        new Entry(lines.subList(3, 6)),
                        new Entry(lines.subList(6, 9)),
                        new Entry(lines.subList(9, 12)));
                assertEquals(expected, entries);
            }
        }

        private MultiLineReader createReader() {
            return new MultiLineReader(new StringReader(content), Pattern.compile("^Entry.*$"));
        }
    }

    @Nested
    @DisplayName("Call iterator(), spliterator() and entries() more than once")
    class IterateMoreThanOnce {

        @Test
        @DisplayName("iterator() after iterator()")
        void testIteratorAfterIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::iterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::iterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("spliterator() after iterator()")
        void testSpliteratorAfterIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::iterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::spliterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("entries() after iterator()")
        void testEntriesAfterIterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::iterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::entries);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("iterator() after spliterator()")
        void testIteratorAfterSpliterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::spliterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::iterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("spliterator() after spliterator()")
        void testSpliteratorAfterSpliterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::spliterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::spliterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("entries() after spliterator()")
        void testEntriesAfterSpliterator() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::spliterator);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::entries);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("iterator() after entries()")
        void testIteratorAfterEntries() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::entries);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::iterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("spliterator() after entries()")
        void testSpliteratorAfterEntries() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::entries);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::spliterator);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        @Test
        @DisplayName("entries() after entries()")
        void testEntriesAfterEntries() throws IOException {
            try (MultiLineReader reader = createReader()) {
                assertDoesNotThrow(reader::entries);
                IllegalStateException exception = assertThrows(IllegalStateException.class, reader::entries);
                assertEquals(Messages.MultiLineReader.iteratorAlreadyReturned.get(), exception.getMessage());
            }
        }

        private MultiLineReader createReader() {
            return new MultiLineReader(new StringReader(""), l -> true);
        }
    }

    @Nested
    @DisplayName("Entry")
    class EntryTest {

        private List<String> lines = Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2",
                "Entry2", "- Entry2.1", "- Entry2.2",
                "Entry3", "- Entry3.1", "- Entry3.2",
                "Entry4", "- Entry4.1", "- Entry4.2");
        private String content = String.join("\n", lines);

        @Test
        @DisplayName("lines()")
        void testLines() throws IOException {
            try (MultiLineReader reader = createReader()) {
                List<Entry> entries = reader.entries().collect(Collectors.toList());
                assertThat(entries, hasSize(4));
                assertLines(entries.get(0), lines.subList(0, 3));
                assertLines(entries.get(1), lines.subList(3, 6));
                assertLines(entries.get(2), lines.subList(6, 9));
                assertLines(entries.get(3), lines.subList(9, 12));
            }
        }

        private void assertLines(Entry entry, List<String> lines) {
            assertEquals(lines, entry.lines());
            assertInstanceOf(Collections.unmodifiableList(lines).getClass(), entry.lines());
        }

        @Test
        @DisplayName("iterator()")
        void testIterator() {
            Entry entry = getEntry();
            Iterator<String> iterator = entry.iterator();
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertTrue(iterator.hasNext());
            assertEquals("Entry1", iterator.next());
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertTrue(iterator.hasNext());
            assertEquals("- Entry1.1", iterator.next());
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertTrue(iterator.hasNext());
            assertEquals("- Entry1.2", iterator.next());
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertFalse(iterator.hasNext());
            assertThrows(NoSuchElementException.class, iterator::next);
            assertThrows(UnsupportedOperationException.class, iterator::remove);
        }

        private MultiLineReader createReader() {
            return new MultiLineReader(new StringReader(content), Pattern.compile("^Entry.*$"));
        }

        private Entry getEntry() {
            try (MultiLineReader reader = createReader()) {
                return reader.iterator().next();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Nested
        @DisplayName("spliterator()")
        class SpliteratorTest {

            @Test
            @DisplayName("tryAdvance(Consumer)")
            void testTryAdvance() {
                Spliterator<String> spliterator = getSpliterator();
                List<String> list = new ArrayList<>();
                assertTrue(spliterator.tryAdvance(list::add));
                assertTrue(spliterator.tryAdvance(list::add));
                assertTrue(spliterator.tryAdvance(list::add));
                assertFalse(spliterator.tryAdvance(list::add));
                assertEquals(Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2"), list);
            }

            @Test
            @DisplayName("forEachRemaining(Consumer)")
            void testForEachRemaining() {
                Spliterator<String> spliterator = getSpliterator();
                List<String> list = new ArrayList<>();
                spliterator.tryAdvance(s -> { /* ignore */ });
                spliterator.forEachRemaining(list::add);
                assertEquals(Arrays.asList("- Entry1.1", "- Entry1.2"), list);
            }

            @Test
            @DisplayName("trySplit()")
            void testTrySplit() {
                Entry entry = getEntry();
                Spliterator<String> fromEntry = entry.spliterator();
                Spliterator<String> fromList = entry.lines().spliterator();
                while (fromList != null) {
                    assertNotNull(fromEntry);
                    assertCharacteristics(fromEntry, fromList);
                    fromEntry = fromEntry.trySplit();
                    fromList = fromList.trySplit();
                }
                assertNull(fromEntry);
            }

            @Test
            @DisplayName("estimateSize()")
            void testEstimateSize() {
                Entry entry = getEntry();
                Spliterator<String> fromEntry = entry.spliterator();
                Spliterator<String> fromList = entry.lines().spliterator();
                while (fromList != null) {
                    assertNotNull(fromEntry);
                    assertEquals(fromList.estimateSize(), fromEntry.estimateSize());
                    fromEntry = fromEntry.trySplit();
                    fromList = fromList.trySplit();
                }
                assertNull(fromEntry);
            }

            @Test
            @DisplayName("getExactSizeIfKnown()")
            void testGetExactSizeIfKnown() {
                Entry entry = getEntry();
                Spliterator<String> fromEntry = entry.spliterator();
                Spliterator<String> fromList = entry.lines().spliterator();
                while (fromList != null) {
                    assertNotNull(fromEntry);
                    assertEquals(fromList.getExactSizeIfKnown(), fromEntry.getExactSizeIfKnown());
                    fromEntry = fromEntry.trySplit();
                    fromList = fromList.trySplit();
                }
                assertNull(fromEntry);
            }

            @Test
            @DisplayName("getComparator()")
            void testGetComparator() {
                Entry entry = getEntry();
                Spliterator<String> fromEntry = entry.spliterator();
                Spliterator<String> fromList = entry.lines().spliterator();
                if (fromEntry.hasCharacteristics(Spliterator.SORTED)) {
                    assertEquals(fromList.getComparator(), fromEntry.getComparator());
                } else {
                    assertThrows(IllegalStateException.class, fromEntry::getComparator);
                }
            }

            private Spliterator<String> getSpliterator() {
                return getEntry().spliterator();
            }

            private void assertCharacteristics(Spliterator<String> fromEntry, Spliterator<String> fromList) {
                assertTrue(fromEntry.hasCharacteristics(Spliterator.ORDERED));
                assertTrue(fromEntry.hasCharacteristics(Spliterator.IMMUTABLE));
                assertTrue(fromEntry.hasCharacteristics(Spliterator.SIZED));
                assertTrue(fromEntry.hasCharacteristics(Spliterator.NONNULL));
                assertTrue(fromEntry.hasCharacteristics(Spliterator.IMMUTABLE));

                assertEquals(fromList.hasCharacteristics(Spliterator.DISTINCT), fromEntry.hasCharacteristics(Spliterator.DISTINCT));
                assertEquals(fromList.hasCharacteristics(Spliterator.CONCURRENT), fromEntry.hasCharacteristics(Spliterator.CONCURRENT));
                assertEquals(fromList.hasCharacteristics(Spliterator.SUBSIZED), fromEntry.hasCharacteristics(Spliterator.SUBSIZED));
            }
        }

        @Test
        @DisplayName("stream()")
        void testStream() {
            List<String> list = getEntry().stream()
                    .collect(Collectors.toList());
            assertThat(list, contains("Entry1", "- Entry1.1", "- Entry1.2"));
        }

        @Test
        @DisplayName("forEach(Consumer)")
        void testForEach() {
            List<String> list = new ArrayList<>();
            getEntry().forEach(list::add);
            assertThat(list, contains("Entry1", "- Entry1.1", "- Entry1.2"));
        }

        @ParameterizedTest(name = "{1}")
        @ArgumentsSource(EntryEqualsArguments.class)
        @DisplayName("equals(Object)")
        void testEquals(Entry entry, Object o, boolean expected) {
            assertEquals(expected, entry.equals(o));
        }

        @Test
        @DisplayName("hashCode()")
        void testHashCode() {
            Entry entry = getEntry();
            assertEquals(entry.lines().hashCode(), entry.hashCode());
        }

        @Test
        @DisplayName("toString()")
        void testToString() {
            Entry entry = getEntry();
            assertEquals(String.format("Entry1%n- Entry1.1%n- Entry1.2"), entry.toString());
        }
    }

    private static final class EntryEqualsArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            Entry entry = new Entry(Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2"));
            Entry equalEntry = new Entry(new ArrayList<>(entry.lines()));
            Entry unequalEntry1 = new Entry(Arrays.asList("Entry1", "- Entry1.1"));
            Entry unequalEntry2 = new Entry(Arrays.asList("Entry1", "- Entry1.1", "- Entry1.2", "- Entry1.3"));

            Arguments[] arguments = new Arguments[] {
                    arguments(entry, entry, true),
                    arguments(entry, null, false),
                    arguments(entry, "string", false),
                    arguments(entry, equalEntry, true),
                    arguments(entry, unequalEntry1, false),
                    arguments(entry, unequalEntry2, false),
            };
            return Arrays.stream(arguments);
        }
    }
}
