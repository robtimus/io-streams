/*
 * LimitExceededException.java
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

import java.io.IOException;

/**
 * Exception thrown when the limit of a {@link LimitReader}, {@link LimitWriter}, {@link LimitInputStream} or {@link LimitOutputStream} with strategy
 * {@link LimitExceededStrategy#THROW} was exceeded.
 *
 * @author Rob Spoor
 */
public final class LimitExceededException extends IOException {

    private static final long serialVersionUID = -9143468798886412497L;

    private final long limit;

    /**
     * Creates a new limit exceeded exception.
     *
     * @param limit The limit that was exceeded.
     */
    public LimitExceededException(long limit) {
        super(Messages.LimitExceededException.init(limit));
        this.limit = limit;
    }

    /**
     * Returns the limit that was exceeded.
     *
     * @return The limit that was exceeded.
     */
    public long getLimit() {
        return limit;
    }
}
