package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * An annotation that marks a Java method as JavaScript function.
 * When @JSNoAutoDetect is used on the class - it's required for exporting method to the JS runtime.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSFunction {
    String value() default "";
}
