package io.alicorn.v8.utils;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8ScriptExecutionException;

public class V8Helper {
    public static V8Helper INSTANCE = new V8Helper();
    private Boolean supportsProxy = null;

    private V8Helper() {
    }

    /**
     * @return whether Proxy is supported as a language feature. E.g. current v8 version is at least 4.9.
     */
    public boolean isSupportsProxy(V8 v8) {
        if (supportsProxy == null) {
            try {
                //instead V8.getV8Version() can be compared >= 4.9, but executing empty Proxy is easy and more reliable.
                v8.executeVoidScript("new Proxy({},{})");
                supportsProxy = true;
            } catch (V8ScriptExecutionException e) {
                System.out.println("[warning] Proxy is not supported:"
                        + " features like 'java map to js object' convection are not enabled."
                        + " Required V8 4.9, but current version is " + V8.getV8Version());
                supportsProxy = false;
            }
        }

        return supportsProxy;
    }
}
