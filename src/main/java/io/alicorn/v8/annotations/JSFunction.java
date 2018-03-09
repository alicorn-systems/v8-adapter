package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * Marks a Java method as a Javascript function.
 *
 * This annotation is required for exporting any desired functions to a JS runtime
 * when the {@link JSDisableMethodAutodetect} annotation is present on a class.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSFunction {
    String value() default "";
}
