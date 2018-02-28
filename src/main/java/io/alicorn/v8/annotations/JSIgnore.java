package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * Using this annotation on the methods prevents it from being exported as function/property to JS runtime.
 * When @JSNoAutoDetect is used on the class - this function does nothing.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSIgnore {
    String value() default "";
}
