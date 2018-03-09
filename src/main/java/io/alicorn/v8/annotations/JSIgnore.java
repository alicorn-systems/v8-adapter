package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * Using this annotation on a method prevents it from being exported as
 * a function/property to JS runtime.
 *
 * This annotation has no effect when the {@link JSDisableMethodAutodetect} annotation
 * is present on a class.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSIgnore {
    String value() default "";
}
