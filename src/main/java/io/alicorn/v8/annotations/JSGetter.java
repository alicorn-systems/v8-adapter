package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * An annotation that marks a Java method as JavaScript getter.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSGetter {
    String value() default "";
}
