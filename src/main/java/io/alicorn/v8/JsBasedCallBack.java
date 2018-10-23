package io.alicorn.v8;

import com.eclipsesource.v8.Releasable;

/**
 * If gc v8 executor is specified and Object is found as target type for "JS to Java" transformation -
 * proxy based on this class is used instead.
 */
public interface JsBasedCallBack extends Releasable {
    Object call(Object... args);
}
