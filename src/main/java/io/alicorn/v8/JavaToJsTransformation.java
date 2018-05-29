package io.alicorn.v8;

import com.eclipsesource.v8.V8Object;

/**
 * Overrides default behaviour of transforming classes from Java to JS.
 */
public interface JavaToJsTransformation<T> {
    V8Object transform(T javaObject);
}
