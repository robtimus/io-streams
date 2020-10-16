/*
 * LimitExceededStrategy.java
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

/**
 * The possible strategies that can be used by {@link LimitReader}, {@link LimitWriter}, {@link LimitInputStream} and {@link LimitOutputStream}.
 *
 * @author Rob Spoor
 */
public enum LimitExceededStrategy {

    /**
     * Indicates that any remaining content should be discarded.
     * For {@link LimitReader} and {@link LimitInputStream} this will effectively mean the stream will end.
     */
    DISCARD(false),

    /**
     * Indicates that a {@link LimitExceededException} should be thrown.
     */
    THROW(true),
    ;

    private final boolean throwException;

    LimitExceededStrategy(boolean throwException) {
        this.throwException = throwException;
    }

    boolean throwException() {
        return throwException;
    }
}
