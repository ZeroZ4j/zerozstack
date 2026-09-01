/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
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
package com.zeroz4j.api.i18n;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says that this method turns a message into words once, on purpose, and must not be checked.
 *
 * <p>{@code MessageReadContractTest} fails the build when {@code Message.text()} is called outside
 * an {@code Effect} or a {@code Computed}, because words read once never change again when somebody
 * switches language. Some reads genuinely are once:</p>
 *
 * <ul>
 *   <li>a sentence assembled for one server call and sent, never shown;</li>
 *   <li>a message written into a log line, an exception or an alert that appears and is gone;</li>
 *   <li>a helper called from inside an effect, where the effect is doing the subscribing and this
 *       method cannot see that it is.</li>
 * </ul>
 *
 * <p>Put it on the method, not on the class: a class-wide exemption grows until nothing in that
 * file is checked at all.</p>
 *
 * <p><b>It is an exemption, not a fix.</b> A label on screen that carries this annotation is a
 * label that will still be in the old language after somebody switches, and nothing will say so.
 * </p>
 *
 * @since 0.9.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface ReadsMessagesOnce {

    /**
     * Why this read is a once-only read. Written for whoever finds it in a year.
     *
     * @return the reason
     */
    String value() default "";
}
