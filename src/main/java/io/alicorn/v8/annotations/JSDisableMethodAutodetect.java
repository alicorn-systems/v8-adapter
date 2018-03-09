package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * If a class is marked with this annotation, the {@link io.alicorn.v8.V8JavaAdapter}
 * will only export methods marked with {@link JSGetter}, {@link JSSetter}
 * and {@link JSFunction} to the V8 runtime.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSDisableMethodAutodetect {
    String value() default "";
}
