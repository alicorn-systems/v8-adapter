package io.alicorn.v8;

import com.eclipsesource.v8.V8;
import io.alicorn.v8.ConcurrentV8;
import io.alicorn.v8.ConcurrentV8Runnable;
import io.alicorn.v8.V8JavaAdapter;
import org.junit.Assert;
import org.junit.Test;

public class ConcurrentV8Test {

    public static class Foo {
        final int val;

        public Foo(int val) {
            this.val = val;
        }

        public int getThing() {
            return 3344;
        }

        public int getVal() {
            return val;
        }

        public void whine() throws Exception {
            throw new Exception("Whaaa!");
        }
    }

    int temp = 0;

    @Test
    public void shouldShareV8AcrossThreads() {
        final ConcurrentV8 v8 = new ConcurrentV8();

        Thread thread1 = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    v8.run(new ConcurrentV8Runnable() {
                        @Override
                        public void run(V8 v8) {
                            v8.executeVoidScript("var i = 3000;");
                        }
                    });
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
            }
        });

        thread1.start();
        try {
            thread1.join();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Thread thread2 = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    v8.run(new ConcurrentV8Runnable() {
                        @Override
                        public void run(V8 v8) {
                            v8.executeVoidScript("i += 344;");
                        }
                    });
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
            }
        });

        thread2.start();
        try {
            thread2.join();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    Assert.assertEquals(3344, v8.executeIntegerScript("i"));
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try {
            v8.release();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void shouldShareInjectedObjectsAndClassesAcrossThreads() {
        ConcurrentV8 v8 = new ConcurrentV8();

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    V8JavaAdapter.injectClass(Foo.class, v8);
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        temp = 0;

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    temp = v8.executeIntegerScript("var x = new Foo(30); x.getThing();");
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Assert.assertEquals(3344, temp);

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    V8JavaAdapter.injectObject("fooey", new Foo(9001), v8);
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        temp = 0;

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    temp = v8.executeIntegerScript("fooey.getVal();");
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Assert.assertEquals(9001, temp);
    }

    @Test
    public void shouldHandleExceptions() {
        ConcurrentV8 v8 = new ConcurrentV8();

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) throws Exception {
                    V8JavaAdapter.injectClass(Foo.class, v8);
                }
            });
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try {
            v8.run(new ConcurrentV8Runnable() {
                @Override public void run(V8 v8) throws Exception {
                    v8.executeScript("var x = new Foo(33); x.whine();");
                }
            });

            Assert.fail("Regular concurrent V8 invocations should pass on exceptions.");
        } catch (Throwable e) { }
    }
}