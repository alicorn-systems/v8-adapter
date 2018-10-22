package io.alicorn.v8;

/**
 * If gc v8 executor is specified and Object is found as target type for "JS to Java" transformation -
 * proxy based on this class is used instead.
 */
public interface JsBasedCallBack {
    Object call(Object... args);
}
