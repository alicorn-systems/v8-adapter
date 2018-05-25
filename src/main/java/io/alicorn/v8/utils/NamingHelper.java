package io.alicorn.v8.utils;

import java.util.UUID;

/**
 * Contains utility logic related to naming convention used in the project.
 */
public class NamingHelper {
    public static NamingHelper INSTANCE = new NamingHelper();

    private NamingHelper() {
    }

    public String randomVarName() {
        return "TEMP" + randomId();
    }

    public String randomInstanceAddress() {
        return "OHID" + randomId();
    }

    public String randomInterceptorAddress() {
        return "CICHID" + randomId();
    }

    private String randomId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
