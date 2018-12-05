package io.alicorn.v8;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class V8JavaClassProxyTest {

    public static class Parent {
        public Parent returnMe() {
            return this;
        }
    }

    /**
     * Java compiler creates two .returnMe() methods in Child:
     * - 1st one with return type Child (as declared in source code)
     * - and another bridge method with return type Parent (in order to match pre-1.5 bytecode specification).
     */
    public static class Child extends Parent {

        @Override
        public Child returnMe() {
            return this;
        }
    }

    @Test
    public void shouldInjectObjects() {
        final V8JavaCache cacheStub = null;
        final V8JavaClassInterceptor interceptorStub = null;

        final int expectedMethodsInJSProxy = 1;

        final V8JavaClassProxy proxy = new V8JavaClassProxy(Child.class, interceptorStub, cacheStub);
        final int actualMethodsInJsProxy = proxy.instanceMethods.get("returnMe").getMethodSignatures().size();
        assertEquals(expectedMethodsInJSProxy, actualMethodsInJsProxy);
    }
}
