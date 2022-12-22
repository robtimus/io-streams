/*
 * MultiLineReader.java
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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.io.IOUtils;
import com.github.robtimus.io.stream.MultiLineReader.Entry;

/**
 * A class for reading multiple lines at a time. This can be useful for files where some entries span multiple lines, e.g. log files.
 * <p>
 * Although {@code MultiLineReader} implements {@link Iterable}, it only supports iterating over the entries once. This iterating can be done using
 * {@link #iterator()}, {@link #spliterator()} or {@link #entries()}. Only one of these methods may be used per {@code MultiLineReader} instance,
 * and only once. Attempting to call more than one of these methods, or one of these methods multiple times, will result in an
 * {@link IllegalStateException}.
 *
 * @author Rob Spoor
 */
public final class MultiLineReader implements Iterable<Entry>, Closeable {

    private final BufferedReader reader;

    private final Predicate<? super String> newEntryStart;

    private boolean returnedIterator = false;

    /**
     * Creates a new multi-line reader.
     * This method is short for {@link #MultiLineReader(Reader, Predicate) MultiLineReader(reader, newEntryStart.asPredicate())}.
     *
     * @param reader The backing reader.
     * @param newEntryStart The pattern that determines when a line indicates the start of a new entry.
     * @throws NullPointerException If the reader or pattern is {@code null}.
     */
    public MultiLineReader(Reader reader, Pattern newEntryStart) {
        this(reader, newEntryStart.asPredicate());
    }

    /**
     * Creates a new multi-line reader.
     *
     * @param reader The backing reader.
     * @param newEntryStart The predicate that determines when a line indicates the start of a new entry.
     * @throws NullPointerException If the reader or predicate is {@code null}.
     */
    @SuppressWarnings("resource")
    public MultiLineReader(Reader reader, Predicate<? super String> newEntryStart) {
        this.reader = IOUtils.buffer(Objects.requireNonNull(reader));
        this.newEntryStart = Objects.requireNonNull(newEntryStart);
    }

    /**
     * Returns an iterator over the entries.
     * This method may be called only once, and may not be used in combination with {@link #spliterator()} and {@link #entries()}.
     *
     * @return An iterator over the entries.
     * @throws IllegalStateException If this method is called for a second time,
     *                                   or if {@link #spliterator()} or {@link #entries()} has already been called.
     */
    @Override
    public Iterator<Entry> iterator() {
        if (returnedIterator) {
            throw new IllegalStateException(Messages.MultiLineReader.iteratorAlreadyReturned());
        }
        returnedIterator = true;
        return new EntryIterator();
    }

    /**
     * Returns a {@link Spliterator} over the entries.
     * This method may be called only once, and may not be used in combination with {@link #iterator()} and {@link #entries()}.
     *
     * @return A {@link Spliterator} over the entries.
     * @throws IllegalStateException If this method is called for a second time,
     *                                   or if {@link #spliterator()} or {@link #entries()} has already been called.
     */
    @Override
    public Spliterator<Entry> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
    }

    /**
     * Returns a stream over the entries.
     * This method may be called only once, and may not be used in combination with {@link #iterator()} and {@link #spliterator()}.
     *
     * @return A stream over the entries.
     * @throws IllegalStateException If this method is called for a second time,
     *                                   or if {@link #iterator()} or {@link #spliterator()} has already been called.
     */
    public Stream<Entry> entries() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * An entry that can be read using a {@link MultiLineReader}. It consists of one or more lines.
     * <p>
     * Entries are immutable.
     *
     * @author Rob Spoor
     */
    public static final class Entry implements Iterable<String> {

        private final List<String> lines;

        Entry(List<String> lines) {
            this.lines = Collections.unmodifiableList(lines);
        }

        /**
         * Returns all lines of this entry as a list.
         *
         * @return An unmodifiable, non-empty list containing all lines of this entry.
         */
        public List<String> lines() {
            return lines;
        }

        @Override
        public Iterator<String> iterator() {
            return lines.iterator();
        }

        @Override
        public Spliterator<String> spliterator() {
            // don't create a fresh new spliterator but instead wrap lines.spliterator() to add extra characteristics
            return new EntrySpliterator(lines.spliterator());
        }

        /**
         * Returns a stream over the lines.
         *
         * @return A stream over the lines.
         */
        public Stream<String> stream() {
            return StreamSupport.stream(spliterator(), false);
        }

        /**
         * Performs the given action for each element of the entry until all elements have been processed or the action throws an exception.
         */
        @Override
        public void forEach(Consumer<? super String> action) {
            lines.forEach(action);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            Entry other = (Entry) o;
            return lines.equals(other.lines);
        }

        @Override
        public int hashCode() {
            return lines.hashCode();
        }

        @Override
        public String toString() {
            return String.join(System.getProperty("line.separator"), lines); //$NON-NLS-1$
        }
    }

    private final class EntryIterator implements Iterator<Entry> {

        private Entry nextEntry;

        private String lastLine;
        private boolean initialized;

        @Override
        public boolean hasNext() {
            if (nextEntry == null) {
                try {
                    nextEntry = readNextEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return nextEntry != null;
        }

        @Override
        public Entry next() {
            if (hasNext()) {
                Entry next = nextEntry;
                nextEntry = null;
                return next;
            }
            throw new NoSuchElementException();
        }

        private Entry readNextEntry() throws IOException {
            if (lastLine == null) {
                // either readLine() hasn't been called yet, or the last line has been read
                if (initialized) {
                    return null;
                }
                lastLine = reader.readLine();
                initialized = true;
                if (lastLine == null) {
                    // empty file
                    return null;
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add(lastLine);
            while ((lastLine = reader.readLine()) != null && !newEntryStart.test(lastLine)) {
                lines.add(lastLine);
            }
            // either all lines were read, or lastLine belongs to the next entry
            return new Entry(lines);
        }
    }

    private static final class EntrySpliterator implements Spliterator<String> {

        private final Spliterator<String> spliterator;

        private EntrySpliterator(Spliterator<String> spliterator) {
            this.spliterator = spliterator;
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            return spliterator.tryAdvance(action);
        }

        @Override
        public void forEachRemaining(Consumer<? super String> action) {
            spliterator.forEachRemaining(action);
        }

        @Override
        public Spliterator<String> trySplit() {
            Spliterator<String> split = spliterator.trySplit();
            return split != null ? new EntrySpliterator(split) : null;
        }

        @Override
        public long estimateSize() {
            return spliterator.estimateSize();
        }

        @Override
        public long getExactSizeIfKnown() {
            return spliterator.getExactSizeIfKnown();
        }

        @Override
        public int characteristics() {
            return spliterator.characteristics() | Spliterator.NONNULL | Spliterator.IMMUTABLE;
        }

        @Override
        public Comparator<? super String> getComparator() {
            return spliterator.getComparator();
        }
    }
}
