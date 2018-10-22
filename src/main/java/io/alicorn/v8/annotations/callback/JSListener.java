package io.alicorn.v8.annotations.callback;

import java.lang.annotation.*;

/**
 * Marks a Java functional interface as a Javascript Listener, but not as call-back.
 *
 * This annotation is required for retaining underlying V8 function after 1st call of related JS.
 * Should be used if Js Function is expected to be called multiple times in listener manner
 *  (comparing to call-back function, which is expected to be called only once).
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSListener {
    String value() default "";
}
