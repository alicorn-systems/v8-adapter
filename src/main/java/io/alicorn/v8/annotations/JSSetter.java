/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * An annotation that marks a Java method as JavaScript setter.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 *
 * @see io.alicorn.v8.V8JavaClassProxy#registerGetterAndSetterProperties
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSSetter {
    String value() default "";
}
