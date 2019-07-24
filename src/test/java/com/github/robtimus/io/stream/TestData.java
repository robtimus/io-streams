/*
 * TestData.java
 * Copyright 2019 Rob Spoor
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

@SuppressWarnings("nls")
final class TestData {

    static final String SOURCE = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

    private TestData() {
        throw new Error("cannot create instances of " + getClass().getName());
    }

    // the following classes are not final so they can be spied / mocked

    static class FlushableAppendable implements Appendable, Flushable {

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(char c) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flush() throws IOException {
            // does nothing
        }
    }

    static class CloseableAppendable implements Appendable, Closeable {

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(char c) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws IOException {
            // does nothing
        }
    }

    static class AutoCloseableAppendable implements Appendable, AutoCloseable {

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Appendable append(char c) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws Exception {
            // does nothing
        }
    }
}
