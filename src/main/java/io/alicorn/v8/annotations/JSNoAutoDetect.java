package io.alicorn.v8.annotations;

import java.lang.annotation.*;

/**
 * Mark the class, where only method with @JSGetter/@JSSetter/@JSFunction annotations will be exported to js.
 *
 * @author Alex Trotsenko [alexey.trotsenko@gmail.com]
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSNoAutoDetect {
    String value() default "";
}
