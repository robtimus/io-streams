/*
 * TestBase.java
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import org.junit.jupiter.api.function.Executable;

@SuppressWarnings("nls")
abstract class TestBase {

    static final String SOURCE = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
    static final String LONG_SOURCE;

    static {
        StringBuilder sb = new StringBuilder(1000 * SOURCE.length());
        for (int i = 0; i < 1000; i++) {
            sb.append(SOURCE);
        }
        LONG_SOURCE = sb.toString();
    }

    void assertClosed(Executable executable) {
        IOException thrown = assertThrows(IOException.class, executable);
        assertEquals(Messages.stream.closed(), thrown.getMessage());
    }
}
