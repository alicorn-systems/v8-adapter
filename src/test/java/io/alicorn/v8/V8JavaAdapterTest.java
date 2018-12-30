package io.alicorn.v8;

import com.eclipsesource.v8.*;
import com.eclipsesource.v8.utils.V8ObjectUtils;
import io.alicorn.v8.annotations.JSDisableMethodAutodetect;
import io.alicorn.v8.annotations.JSGetter;
import io.alicorn.v8.annotations.JSSetter;
import io.alicorn.v8.annotations.JSStaticFunction;
import io.alicorn.v8.annotations.callback.JSListener;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.StringContains;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class V8JavaAdapterTest {
//Setup classes////////////////////////////////////////////////////////////////
    private interface Baz {
        Foo doFooInterface(Foo foo);
    }

    private interface Bar {
        int doInterface(int args);
    }

    private interface CallBack {
        Object call(Object... args);
    }

    @JSListener
    private interface Listener extends CallBack {
        @Override Object call(Object... args);
    }

    private static final class Foo {
        public int i;
        public Foo(int i) { this.i = i; }
        public static int doStatic() { return 9001; }
        public int doInstance(int i) { return this.i + i; }
        public int doInstance(int i, int i2) { return this.i + (i * i2); }
        public void add(Foo foo) { this.i += foo.i; }
        public void add(Bar bar) { this.i = bar.doInterface(this.i); }
        public void addBaz(Baz baz) { this.i = baz.doFooInterface(this).getI(); }
        public Object invokeCallBack(CallBack callBack, Object... args) {return callBack.call(args); }
        public Object invokeCallBackTwice(CallBack callBack, Object... args) {
            final Object result = callBack.call(args);

            //should throw here as callback is already released
            callBack.call(args);

            return result;
        }

        public Object invokeListenerTwice(Listener listener, Object... args) {
            final Object result = listener.call(args);

            //should NOT throw here as it's listener, but not call-back
            listener.call(args);

            return result;
        }

        public Object invokeAndReleaseListener(Listener listener, Object... args) {
          final Object jsCallResult = invokeAndManuallyReleaseInner(listener, args);
          return jsCallResult;
        }

        public Object reInvokeReleasedListener(Listener listener, Object... args) throws V8ScriptExecutionException {
          final Object jsCallResult = invokeAndManuallyReleaseInner(listener, args);

          //should throw here as listener is already released manually
          listener.call(args);

          return jsCallResult;
        }

        private Object invokeAndManuallyReleaseInner(Listener listener, Object[] args) {
          final Object call = listener.call(args);
          //despite Listener does not implement Releasable it's safe to cast as it's implemented by Proxy
          ((Releasable) listener).release();
          return call;
        }


      public String doString(String s) { return s; }
        public int getI() { return i; }
        public Foo copy() { return new Foo(i); }
        public int doArray(int[] a) {
            int ret = 0;
            for (int i = 0; i < a.length; i++) {
                ret += a[i];
            }
            return ret;
        }
        public int doNArray(int[][] a) {
            int ret = 0;
            for (int i = 0; i < a.length; i++) {
                ret += doArray(a[i]);
            }
            return ret;
        }
        public int doVarargs(int a, int b, int... c) {
            int ret = a + b;
            for (int i = 0; i < c.length; i++) {
                ret += c[i];
            }
            return ret;
        }
    }

    @JSDisableMethodAutodetect
    private static final class FooNoAutoDetect {
        @JSStaticFunction
        public static int doStaticAnnotated() { return 9001; }

        public static int doStaticNotAnnotated() { return 9001; }
    }

    private static final class InterceptableFoo {
        public int i;
        public InterceptableFoo(int i) { this.i = i; }
        public void add(int i) { this.i += i; }
        public int getI() { return i; }
        public void setI(int i) { this.i = i; }
    }

    private abstract static class WannabeBeanBase {
        public int i = 0;
        public int j = 0;
        public WannabeBeanBase() {}
        public int getI() { return i / 2; }
        public void setI(int value) { i = value; }
        public int getJ() { return j; }
        public void setJ(int value) { j = value * 2; }
        public boolean isFullySetUp() { return i != 0 && j != 0; }
    }

    private static class WannabeBean extends WannabeBeanBase {
        public WannabeBean() {}
        @JSGetter @Override public int getI() { return super.getI(); }
        @JSSetter @Override public void setI(int value) { super.setI(value); }
        @JSGetter @Override public int getJ() { return super.getJ(); }
        @JSSetter @Override public void setJ(int value) { super.setJ(value); }
        @JSGetter @Override public boolean isFullySetUp() { return super.isFullySetUp(); }
    }

    @JSDisableMethodAutodetect
    private static final class WannabeBeanNoAutoDetect extends WannabeBean {
        public WannabeBeanNoAutoDetect() {
        }
    }

    private static class WannabeBeanNoAnnotations extends WannabeBeanBase {
        public WannabeBeanNoAnnotations() {}
        @Override public int getI() { return super.getI(); }
        @Override public void setI(int value) { super.setI(value); }
        @Override public int getJ() { return super.getJ(); }
        @Override public void setJ(int value) { super.setJ(value); }
        @Override public boolean isFullySetUp() { return super.isFullySetUp(); }
    }

    @JSDisableMethodAutodetect
    private static final class WannabeBeanNoAnnotationsNoAutoDetect extends WannabeBeanNoAnnotations {
        public WannabeBeanNoAnnotationsNoAutoDetect() {
        }
    }

    private static class NotBean {
        public NotBean() {}
        public int i = 0;
        public int j = 0;
        public int incrementI() { return ++i; }
        public int decrementj() { return --j; }

    }

    @JSDisableMethodAutodetect
    private static final class NotBeanNoAutoDetect extends NotBean {
        public NotBeanNoAutoDetect() {
        }
    }

    @JSDisableMethodAutodetect
    private static final class NotBeanAnnotatedNoAutoDetect extends NotBean {
        public NotBeanAnnotatedNoAutoDetect() {}
        @JSGetter public int incrementI() { return super.incrementI(); }
        @JSSetter public void assignToj(int newJ) {
            this.j = newJ;
        }
        @JSGetter public int sum() {
            return i + j;
        }
    }


    private static final class IncompleteBeanOne {
        public int i = 0;
        public IncompleteBeanOne() { }
        @JSGetter public int getI() { return 3344; }
    }

    private static final class IncompleteBeanTwo {
        public int j = 0;
        public IncompleteBeanTwo() { }
        @JSSetter public void setJ(int value) { j = value; }
        @JSGetter public int getDecoratedJ() { return j; }
    }

    private static final class FooInterceptor implements V8JavaClassInterceptor<InterceptableFoo> {

        @Override public Object objectInjectorOverride(InterceptableFoo object) { return null; }

        @Override public String getConstructorScriptBody() {
            return "var i = 0;\n" +
                    "this.getI = function() { return i; };\n" +
                    "this.setI = function(other) { i = other; };\n" +
                    "this.add = function(other) { i = i + other; };\n" +
                    "this.onJ2V8Inject = function(context) { i = context.get(\"i\"); };\n" +
                    "this.onJ2V8Extract = function(context) { context.set(\"i\", i); };";
        }

        @Override public void onInject(V8JavaClassInterceptorContext context, InterceptableFoo object) {
            context.set("i", object.i);
        }

        @Override public void onExtract(V8JavaClassInterceptorContext context, InterceptableFoo object) {
            object.i = V8JavaObjectUtils.widenNumber(context.get("i"), Integer.class);
        }
    }

    private static final class Fooey {
        public int i = 0;
        public Fooey(int i) { this.i = i; }
        public void doInstance(InterceptableFoo foo) { this.i += foo.getI(); }
        public void setI(int i) { this.i = i; }
        public int getI() { return i; }
    }

    private static final class NativeJsObjectReader {

        private final String yKey = "y";

        public NativeJsObjectReader() {}

        public Object readJsObjectAsMapAndGetY(Map mapWithYProperty) {
            return mapWithYProperty.get(yKey);
        }

        public Object readJsObjectAsV8ObjectAndGetY(V8Object objectWithYProperty) {
            final Object yValue = objectWithYProperty.get(yKey);
            objectWithYProperty.release();
            return yValue;
        }

        public Object readJsObjectAsJavaObjectAndTryGetY(Object possibleMapWithYProperty) {
            if (!(possibleMapWithYProperty instanceof Map)) {
                throw new IllegalArgumentException("Can't read from object, which is not map, but " + possibleMapWithYProperty);
            }

            return ((Map) possibleMapWithYProperty).get(yKey);
        }
    }

    private static final class RawMapHolder {
        Map<String, ?> map;

        public Map<String, ?> getMap() {
            return map;
        }

        public void setMap(Map<String, ?> map) {
            this.map = map;
        }
    }

    private static final class RawListHolder {
        List<?> list;

        public List<?> getMap() {
            return list;
        }

        public void setList(List<?> list) {
            this.list = list;
        }
    }

    private static final class StringMapHolder {
        Map<String, String> map;

        public Map<String, String> getMap() {
            return map;
        }

        public void setMap(Map<String, String> map) {
            this.map = map;
        }
    }

    private static final class NativeJsFunctionReader {
        public NativeJsFunctionReader() {}

        public void sumAndNotify(int left, int right, V8Function callBack) {
            final int sum = left + right;
            final List<Integer> callBackArg = Collections.singletonList(sum);

            final V8 v8 = callBack.getRuntime();
            final V8Array v8Args = V8ObjectUtils.toV8Array(v8, callBackArg);
            callBack.call(v8, v8Args);

            v8Args.release();
            callBack.release();
        }

        public void sumAndAndTryToNotify(int left, int right, Object possibleCallBack) {
            final int sum = left + right;
            final List<Integer> callBackArg = Collections.singletonList(sum);

            if (!(possibleCallBack instanceof JsBasedCallBack)) {
              throw new IllegalArgumentException("Can't call object, which is not JsBasedCallBack, but " + possibleCallBack);
            }

            final JsBasedCallBack callBack = (JsBasedCallBack) possibleCallBack;
            callBack.call(sum);
        }
    }

    private static final class NativeJsArrayReader {

        private final int firstPostition = 0;

        public NativeJsArrayReader() {}

        public Object readJsArrayAsJavaArrayAndGet1st(Integer[] intsArray) {
            return intsArray[firstPostition];
        }

        public Object readJsArrayAsJavaListAndGet1st(List<Integer> intsList) {
            return intsList.get(firstPostition);
        }

        public Object readJsArrayAsJavaRawArrayAndGet1st(Object[] objectsArray) {
            return objectsArray[firstPostition];
        }

        public Object readJsArrayAsRawJavaListAndGet1st(List objectsList) {
            return objectsList.get(firstPostition);
        }

        public Object readJsArrayAsV8ArrayAndGet1st(V8Array array) {
            final Object firstValue = array.get(firstPostition);
            array.release();
            return firstValue;
        }

        public Object readJsArrayAsJavaObjectAndTryGet1st(Object possibleList) {
            if (!(possibleList instanceof List)) {
                throw new IllegalArgumentException("Can't read from object, which is not list, but " + possibleList);
            }

            return ((List) possibleList).get(firstPostition);
        }
    }

    private static final class JsNullReader {
        public JsNullReader() {}

        public int readAndGetInt(int arg) {
            return arg;
        }

        public Integer readAndGetInteger(Integer arg) {
            return arg;
        }

        public Foo readAndGetJavaClass(Foo arg) {
            return arg;
        }

        public Foo readAndGetJavaFunctionalInterface(Foo arg) {
            return arg;
        }
    }

//Tests////////////////////////////////////////////////////////////////////////

    private V8 v8;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        v8 = V8.createV8Runtime();
        V8JavaAdapter.injectClass(Foo.class, v8);
    }

    @After
    public void teardown() {
        V8JavaObjectUtils.releaseV8Resources(v8);
        v8.release(true);
    }

    @Test
    public void shouldInjectObjects() {
        V8JavaAdapter.injectObject("bar", new Bar() {
            @Override public int doInterface(int args) {
                return args * 2;
            }
        }, v8);
        Assert.assertEquals(10, v8.executeIntegerScript("bar.doInterface(5);"));
        V8JavaAdapter.injectObject("bar", new Bar() {
            @Override public int doInterface(int args) {
                return args * 4;
            }
        }, v8);
        Assert.assertEquals(20, v8.executeIntegerScript("bar.doInterface(5);"));
    }

    @Test
    public void shouldInjectClasses() {
        int i = new Random().nextInt(1000);
        Assert.assertEquals(i, v8.executeIntegerScript(String.format("var x = new Foo(%d); x.getI();", i)));
    }

    @Test
    public void shouldHandleStaticInvocations() {
        Assert.assertEquals(9001, v8.executeIntegerScript("Foo.doStatic();"));
    }

    @Test
    public void shouldHandleAnnotatedStaticInvocationsNoAutoDetect() {
        V8JavaAdapter.injectClass(FooNoAutoDetect.class, v8);

        Assert.assertEquals(9001, v8.executeIntegerScript("FooNoAutoDetect.doStaticAnnotated();"));
    }

    @Test
    public void shouldNotHandleNotAnnotatedStaticInvocationsNoAutoDetect() {
        V8JavaAdapter.injectClass(FooNoAutoDetect.class, v8);

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectMessage(StringContains.containsString("FooNoAutoDetect.doStaticNotAnnotated is not a function"));
        v8.executeScript("FooNoAutoDetect.doStaticNotAnnotated();");
    }

    @Test
    public void shouldHandleInstanceInvocations() {
        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new Foo(3300); x.doInstance(44);"));
        Assert.assertEquals(9000, v8.executeIntegerScript("var x = new Foo(3000); x.doInstance(3000, 2);"));
    }

    @Test
    public void shouldHandleComplexArguments() {
        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new Foo(3300); x.add(new Foo(44)); x.getI();"));
    }

    @Test
    public void shouldHandleFunctionalArguments() {
        Assert.assertEquals(1500, v8.executeIntegerScript("var x = new Foo(3000); x.add(function(i) { return i / 2; }); x.getI();"));
        Assert.assertEquals(1500, v8.executeIntegerScript("var x = new Foo(3000); x.addBaz(function(foo) { return new Foo(foo.getI() / 2); }); x.getI();"));
    }

    @Test
    public void shouldHandleFunctionalArgumentsWithVarArg() {
        final int one = 1;
        final int two = 2;
        final int three = 3;
        final int sum = one + two + three;

        Assert.assertEquals(sum, v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.invokeCallBack(sumCallBack, " + one + ", " + two + ", " + three + ");"));

        final int four = 4;
        Assert.assertEquals(sum, v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.invokeCallBack(sumCallBack, " + one + ", " + two + ", " + three + ", " + four + ");"));
    }

    //TODO: remove this test, this is no longer something to test because java 8 or later could recall the same method (list.forEach for example)
    @Test
    public void shouldNotThrowIfFunctionCallBackArgumentsIsCalledTwice() {//should no longer throw
        final int one = 1;
        final int two = 2;
        final int three = 3;
        final int sum = one + two + three;

        //thrown.expect(V8ScriptExecutionException.class);
        //thrown.expectMessage(StringContains.containsString("Object released"));

        //should throw here
        final Object actualResult = v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.invokeCallBackTwice(sumCallBack, " + one + ", " + two + ", " + three + ");");
    }

    @Test
    public void shouldAllowCallingFunctionListenerArgumentsTwice() {
        final int one = 1;
        final int two = 2;
        final int three = 3;
        final int sum = one + two + three;

        Assert.assertEquals(sum, v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.invokeListenerTwice(sumCallBack, " + one + ", " + two + ", " + three + ");"));
    }

    @Test
    public void functionalListenerArgumentsShouldBeReleasable() {
      final int one = 1;
      final int two = 2;
      final int three = 3;
      final int sum = one + two + three;

      Assert.assertEquals(sum, v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.invokeAndReleaseListener(sumCallBack, " + one + ", " + two + ", " + three + ");"));
    }

    @Test
    public void shouldThrowIfFunctionalListenerArgumentsIsReleasedAndReInvokedAgain() {
      final int one = 1;
      final int two = 2;
      final int three = 3;
      final int sum = one + two + three;

      thrown.expect(Exception.class);
      thrown.expectMessage(StringContains.containsString("Object released"));

      Assert.assertEquals(sum, v8.executeScript("var x = new Foo(0); var sumCallBack = function(arg1, arg2, arg3) { return arg1 + arg2 + arg3; }; x.reInvokeReleasedListener(sumCallBack, " + one + ", " + two + ", " + three + ");"));
    }

    @Test
    public void shouldHandleComplexReturnTypes() {
        int i = new Random().nextInt(1000);
        Assert.assertEquals(i, v8.executeIntegerScript(String.format("var x = new Foo(%d); x.copy().getI();", i)));
    }

    @Test
    public void shouldHandleStringArguments() {
        Assert.assertEquals("aStringArgument", v8.executeStringScript("var x = new Foo(9001); x.doString(\"aStringArgument\");"));
    }

    @Test
    public void shouldHandleObjectArrays() {
        V8JavaAdapter.injectObject("objectArray", new String[] {"Hello", "World"}, v8);
        Assert.assertEquals("Hello", v8.executeStringScript("objectArray.get(0)"));
        Assert.assertEquals("World", v8.executeStringScript("objectArray.get(1)"));
    }

    @Test
    public void shouldInterceptClasses() {
        V8JavaAdapter.injectClass(InterceptableFoo.class, new FooInterceptor(), v8);
        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new InterceptableFoo(0); x.add(3344); x.getI();"));
        V8JavaAdapter.injectObject("bazz", new Fooey(3000), v8);
        Assert.assertEquals(9001, v8.executeIntegerScript("x.setI(6001); bazz.doInstance(x); bazz.getI();"));
        InterceptableFoo foo = new InterceptableFoo(4444);
        V8JavaAdapter.injectObject("foobar", foo, v8);
        Assert.assertEquals(8888, v8.executeIntegerScript("foobar.add(4444); foobar.getI();"));
        Assert.assertEquals(8888, v8.executeIntegerScript("bazz.setI(0); bazz.doInstance(foobar); bazz.getI();"));
        Assert.assertEquals(8888, foo.getI());
    }

    @Test
    public void shouldHandleVarargs() {
        Assert.assertEquals(30, v8.executeIntegerScript("var x = new Foo(3000); x.doVarargs(10, 20);"));
        Assert.assertEquals(60, v8.executeIntegerScript("var x = new Foo(3000); x.doVarargs(10, 20, 30);"));
        Assert.assertEquals(100, v8.executeIntegerScript("var x = new Foo(3000); x.doVarargs(10, 20, 30, 40);"));
    }

    @Test
    public void shouldHandleArrays() {
        Assert.assertEquals(30, v8.executeIntegerScript("var x = new Foo(3000); x.doArray([10, 15, 5]);"));
        Assert.assertEquals(70, v8.executeIntegerScript("var x = new Foo(3000); x.doNArray([[10, 15], [20, 25]]);"));
    }

    @Test
    public void shouldNotGarbageCollectLivingObjects() {
        v8.executeVoidScript("var x = new Foo(1000);");
        for (int i = 0; i < 250; i++) {
            Assert.assertEquals(3000, v8.executeIntegerScript("x.doInstance(2000);"));
            Assert.assertTrue(
                    V8JavaAdapter.getCacheForRuntime(v8).identifierToJavaObjectMap.get(
                    v8.executeStringScript("x.____JavaObjectHandleID____;")).get() != null);
            V8JavaAdapter.getCacheForRuntime(v8).removeGarbageCollectedJavaObjects();
            System.gc();
        }
    }

    @Test
    public void shouldGeneratePropertiesForGettersAndSettersWithAnnotations() {
        final String readBooleanGetterScript = "x.fullySetUp;";
        V8JavaAdapter.injectClass(WannabeBean.class, v8);

        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new WannabeBean(); x.i = 6688; x.i;"));
        Assert.assertEquals(false, v8.executeScript(readBooleanGetterScript));

        Assert.assertEquals(6688, v8.executeIntegerScript("x.j = 3344; x.j;"));
        Assert.assertEquals(true, v8.executeBooleanScript(readBooleanGetterScript));
    }

    @Test
    public void shouldGeneratePropertiesForGettersAndSettersWithAnnotationsNoAutoDetect() {
        final String readBooleanGetterScript = "x.fullySetUp;";
        V8JavaAdapter.injectClass(WannabeBeanNoAutoDetect.class, v8);

        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new WannabeBeanNoAutoDetect(); x.i = 6688; x.i;"));
        Assert.assertEquals(false, v8.executeScript(readBooleanGetterScript));

        Assert.assertEquals(6688, v8.executeIntegerScript("x.j = 3344; x.j;"));
        Assert.assertEquals(true, v8.executeBooleanScript(readBooleanGetterScript));
    }

    @Test
    public void shouldGeneratePropertiesForGettersAndSettersWithoutAnnotations() {
        final String readBooleanGetterScript = "x.fullySetUp;";
        V8JavaAdapter.injectClass(WannabeBeanNoAnnotations.class, v8);

        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new WannabeBeanNoAnnotations(); x.i = 6688; x.i;"));
        Assert.assertEquals(false, v8.executeScript(readBooleanGetterScript));

        Assert.assertEquals(6688, v8.executeIntegerScript("x.j = 3344; x.j;"));
        Assert.assertEquals(true, v8.executeBooleanScript(readBooleanGetterScript));
    }

    @Test
    public void shouldNotGeneratePropertiesForGettersAndSettersWithoutAnnotationsNoAutoDetect() {
        final String readBooleanGetterScript = "x.fullySetUp;";
        V8JavaAdapter.injectClass(WannabeBeanNoAnnotationsNoAutoDetect.class, v8);

        Assert.assertNotEquals(3344, v8.executeIntegerScript("var x = new WannabeBeanNoAnnotationsNoAutoDetect(); x.i = 6688; x.i;"));
        Assert.assertEquals(V8.getUndefined(), v8.executeScript(readBooleanGetterScript));

        Assert.assertNotEquals(6688, v8.executeIntegerScript("x.j = 3344; x.j;"));
        Assert.assertEquals(V8.getUndefined(), v8.executeScript(readBooleanGetterScript));
    }

    @Test
    public void shouldNotGeneratePropertiesWhenNoGettersAndSettersByConvention() {
        V8JavaAdapter.injectClass(NotBean.class, v8);
        v8.executeScript("var x = new NotBean();");

        Assert.assertEquals(1, v8.executeIntegerScript("x.incrementI();"));

        final V8Object jsIncFunction = v8.executeObjectScript("x.incrementI;");
        Assert.assertTrue(jsIncFunction instanceof V8Function);
        jsIncFunction.release();

        Assert.assertEquals(2, v8.executeIntegerScript("x.incrementI();"));


        Assert.assertEquals(-1, v8.executeIntegerScript("x.decrementj();"));

        final V8Object jsDecFunction = v8.executeObjectScript("x.decrementj;");
        Assert.assertTrue(jsDecFunction instanceof V8Function);
        jsDecFunction.release();

        Assert.assertEquals(-2, v8.executeIntegerScript("x.decrementj();"));
    }

    @Test
    public void shouldGeneratePropertiesWhenNoGettersAndSettersByConventionWithAnnotationsNoAutoDetect() {
        V8JavaAdapter.injectClass(NotBeanAnnotatedNoAutoDetect.class, v8);
        v8.executeScript("var x = new NotBeanAnnotatedNoAutoDetect();");

        final int expectedX = 1;
        //test getter
        Assert.assertEquals(expectedX, v8.executeScript("x.incrementI;"));

        final int newJ = 3;
        //test setter
        v8.executeScript("x.assignToj = " + newJ + ";");

        final int expectedSum = expectedX + newJ;
        //test getter
        Assert.assertEquals(expectedSum, v8.executeScript("x.sum;"));
    }

    @Test
    public void shouldNotGenerateInstanceFunctionsWithoutAnnotationsNoAutoDetect() {
        V8JavaAdapter.injectClass(NotBeanNoAutoDetect.class, v8);
        v8.executeScript("var x = new NotBeanNoAutoDetect();");

        final V8Object jsIncFunction = v8.executeObjectScript("x.incrementI;");
        Assert.assertEquals(V8.getUndefined(), jsIncFunction);
        jsIncFunction.release();

        final V8Object jsDecFunction = v8.executeObjectScript("x.decrementj;");
        Assert.assertEquals(V8.getUndefined(), jsDecFunction);
        jsDecFunction.release();

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectMessage(StringContains.containsString("x.incrementI is not a function"));
        v8.executeScript("x.incrementI();");
    }

    @Test
    public void shouldInjectAsymmetricGettersAndSetters() {
        V8JavaAdapter.injectClass(IncompleteBeanOne.class, v8);
        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new IncompleteBeanOne(); x.i;"));

        V8JavaAdapter.injectClass(IncompleteBeanTwo.class, v8);
        Assert.assertEquals(6688, v8.executeIntegerScript("var x = new IncompleteBeanTwo(); x.j = 6688; x.decoratedJ;"));
    }

    @Test
    public void shouldInjectAsymmetricGettersAndIgnoreNewValueAssigment() {
        V8JavaAdapter.injectClass(IncompleteBeanOne.class, v8);
        Assert.assertEquals(3344, v8.executeIntegerScript("var x = new IncompleteBeanOne(); x.i = 'new_value'; x.i;"));
    }

    @Test
    public void shouldInjectAsymmetricSettersAndGetterUndefined() {
        V8JavaAdapter.injectClass(IncompleteBeanTwo.class, v8);
        Assert.assertEquals(V8.getUndefined(), v8.executeScript("var x = new IncompleteBeanTwo(); x.j = 6688; x.j;"));
    }

//    - Test Map<String, Integer>
////    - Test Map<String, Foo>

    @Test
    public void shouldReadJsObjectAsMap() {
        V8JavaAdapter.injectClass(NativeJsObjectReader.class, v8);
        final String done = "done";
        Assert.assertEquals(done, v8.executeScript("var x = new NativeJsObjectReader(); var y = x.readJsObjectAsMapAndGetY({y: '" + done + "'}); y;"));
    }

    @Test
    public void shouldReadJsObjectAsMapWithPreviouslyInjectedObject() {
        final String holderJsName = "holder";
        final RawMapHolder holder = new RawMapHolder();
        V8JavaAdapter.injectObject(holderJsName, holder, v8);

        final WannabeBean wannabeBean = new WannabeBean();
        final String wannabeBeanJsName = "bean";
        V8JavaAdapter.injectObject(wannabeBeanJsName, wannabeBean, v8);

        v8.executeVoidScript(("var y = " + holderJsName + ".setMap({y: " + wannabeBeanJsName + "}); y;"));

        final Map<String, Object> expected = new HashMap<String, Object>();
        expected.put("y", wannabeBean);
        Assert.assertEquals(expected, holder.getMap());
    }

    @Test
    public void shouldReadJsObjectAsGenericMap() {
        final String holderJsName = "holder";
        final StringMapHolder holder = new StringMapHolder();
        V8JavaAdapter.injectObject(holderJsName, holder, v8);

        final Integer one = 1;

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(IllegalArgumentException.class));
        thrown.expectMessage(StringContains.containsString("No signature exists for setMap"));

        v8.executeVoidScript(("var y = " + holderJsName + ".setMap({y: " + one + "}); y;"));
    }

    @Test
    public void shouldReadJsObjectAsV8Object() {
        V8JavaAdapter.injectClass(NativeJsObjectReader.class, v8);
        final String done = "done";
        Assert.assertEquals(done, v8.executeScript("var x = new NativeJsObjectReader(); var y = x.readJsObjectAsV8ObjectAndGetY({y: '" + done + "'}); y;"));
    }

    @Test
    public void shouldReadJsObjectAsJavaObject() {
        V8JavaAdapter.injectClass(NativeJsObjectReader.class, v8);
        final String done = "done";
        Assert.assertEquals(done, v8.executeScript("var x = new NativeJsObjectReader(); var y = x.readJsObjectAsJavaObjectAndTryGetY({y: '" + done + "'}); y;"));
    }

    @Test
    public void shouldReadJsArrayAsJavaArray() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsJavaArrayAndGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadJsArrayAsJavaList() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsJavaListAndGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadJsArrayAsJavaRawArray() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsJavaRawArrayAndGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadJsArrayAsRawJavaList() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsRawJavaListAndGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadArrayAsV8Array() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsV8ArrayAndGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadArrayAsJavaObject() {
        V8JavaAdapter.injectClass(NativeJsArrayReader.class, v8);
        final Integer nine = 9;
        Assert.assertEquals(nine, v8.executeScript("var x = new NativeJsArrayReader(); var y = x.readJsArrayAsJavaObjectAndTryGet1st([" + nine + ",8,7]); y;"));
    }

    @Test
    public void shouldReadFunctionAsV8Function() {
        V8JavaAdapter.injectClass(NativeJsFunctionReader.class, v8);

        final AtomicInteger sumContainer = new AtomicInteger();
        V8JavaAdapter.injectObject("sumContainer", sumContainer, v8);

        final int one = 1;
        final int two = 2;
        final int sum = one + two;

        v8.executeScript("var x = new NativeJsFunctionReader(); var y = x.sumAndNotify(" + one + ", " + two + ", function(sum) { sumContainer.set(sum); } ); y;");

        Assert.assertEquals(sum, sumContainer.get());
    }

    @Test
    public void shouldReadFunctionAsJavaObject() {
        V8JavaAdapter.injectClass(NativeJsFunctionReader.class, v8);

        final AtomicInteger sumContainer = new AtomicInteger();
        V8JavaAdapter.injectObject("sumContainer", sumContainer, v8);

        final int one = 1;
        final int two = 2;
        final int sum = one + two;

        final Executor executorStub = new Executor() {
            @Override public void execute(Runnable command) {
                //stub, which does nothing
            }
        };

        V8JavaObjectUtils.setGcExecutor(v8, executorStub);
        v8.executeScript("var x = new NativeJsFunctionReader(); var y = x.sumAndAndTryToNotify(" + one + ", " + two + ", function(sum) { sumContainer.set(sum); } ); y;");
        V8JavaObjectUtils.removeGcExecutor(v8);

        Assert.assertEquals(sum, sumContainer.get());
    }


    @Test
    public void shouldNotReadFunctionAsJavaObjectWithoutGcExecutor() {
        V8JavaAdapter.injectClass(NativeJsFunctionReader.class, v8);

        final AtomicInteger sumContainer = new AtomicInteger();
        V8JavaAdapter.injectObject("sumContainer", sumContainer, v8);

        final int one = 1;
        final int two = 2;
        final int sum = one + two;

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectMessage(StringContains.containsString("No signature exists for sumAndAndTryToNotify"));

        v8.executeScript("var x = new NativeJsFunctionReader(); var y = x.sumAndAndTryToNotify(" + one + ", " + two + ", function(sum) { sumContainer.set(sum); } ); y;");

        Assert.assertEquals(sum, sumContainer.get());
    }

    @Test
    public void shouldReadJsNullAsJavaNull() {
        V8JavaAdapter.injectClass(JsNullReader.class, v8);

        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetInteger(null); y;"));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetJavaClass(null); y;"));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetJavaFunctionalInterface(null); y;"));
    }

    @Test
    public void shouldThrowIllArgExWhenNullToPrimitive() {
        V8JavaAdapter.injectClass(JsNullReader.class, v8);

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(IllegalArgumentException.class));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetInt(null); y;"));
    }

    @Test
    public void shouldReadUndefinedAsNull() {
        V8JavaAdapter.injectClass(JsNullReader.class, v8);

        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetInteger(undefined); y;"));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetJavaClass(undefined); y;"));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetJavaFunctionalInterface(undefined); y;"));
    }

    @Test
    public void shouldThrowIllArgExWhenUndefinedToPrimitive() {
        V8JavaAdapter.injectClass(JsNullReader.class, v8);

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(IllegalArgumentException.class));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetInt(undefined); y;"));
    }

    @Test
    public void shouldThrowIllArgExWhenJsLessArgToPrimitive() {
        V8JavaAdapter.injectClass(JsNullReader.class, v8);

        thrown.expect(V8ScriptExecutionException.class);
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(IllegalArgumentException.class));
        Assert.assertEquals(null, v8.executeScript("var x = new JsNullReader(); var y = x.readAndGetInt(); y;"));
    }

    @Test
    public void shouldChooseCorrectConstructorAndNotPassNulls() {
        V8JavaAdapter.injectClass(File.class, v8);
        v8.executeVoidScript("var f = new File(\"test.txt\");");
    }

    @Test
    public void shouldCustomizeJavaToJSTransformation() {
        final V8JavaClassInterceptor interceptor = new V8JavaClassInterceptor<Map>() {
            @Override
            public Object objectInjectorOverride(Map object) {
                return V8ObjectUtils.toV8Object(v8, object);
            }

            @Override public String getConstructorScriptBody() { return null; }
            @Override public void onInject(V8JavaClassInterceptorContext context, Map object) { }
            @Override public void onExtract(V8JavaClassInterceptorContext context, Map object) { }
        };

        final V8JavaClassInterceptor listInterceptor = new V8JavaClassInterceptor<List>() {
            @Override
            public Object objectInjectorOverride(List object) {
                return V8ObjectUtils.toV8Array(v8, object);
            }

            @Override public String getConstructorScriptBody() { return null; }
            @Override public void onInject(V8JavaClassInterceptorContext context, List object) { }
            @Override public void onExtract(V8JavaClassInterceptorContext context, List object) { }
        };

        V8JavaAdapter.injectClass(HashMap.class, interceptor, v8);
        V8JavaAdapter.injectClass(ArrayList.class, listInterceptor, v8);

        final Map<String, Object> mapToExportToJs = new HashMap<String, Object>();

        final String stringKey = "stringKey";
        final String stringValue = "stringValue";
        mapToExportToJs.put(stringKey, stringValue);

        final List<String> listToExportToJs = new ArrayList<String>();
        final String list1stElement = "myElement";
        listToExportToJs.add(list1stElement);

        final String listKey = "listKey";
        mapToExportToJs.put(listKey, listToExportToJs);

        final RawMapHolder mapProvider = new RawMapHolder();
        mapProvider.setMap(mapToExportToJs);

        final String mapProviderJsName = "mapProvider";
        V8JavaAdapter.injectObject(mapProviderJsName, mapProvider, v8);

        final String holderJsName = "holder";
        AtomicReference<Object> holder = new AtomicReference<Object>();
        V8JavaAdapter.injectObject(holderJsName, holder, v8);


        v8.executeVoidScript("var exportedMap = mapProvider.getMap(); var value = exportedMap['" + stringKey + "']; holder.set(value)");
        Assert.assertEquals(stringValue, holder.get());

        v8.executeVoidScript("var exportedMap = mapProvider.getMap(); var value = exportedMap['" + listKey + "'][0]; holder.set(value)");
        Assert.assertEquals(list1stElement, holder.get());

        final RawListHolder listProvider = new RawListHolder();
        listProvider.setList(listToExportToJs);

        final String listProviderJsName = "listProvider";
        V8JavaAdapter.injectObject(listProviderJsName, listProvider, v8);


        v8.executeVoidScript("var exportedList = listProvider.getMap(); var value = exportedList[0]; holder.set(value)");
        Assert.assertEquals(list1stElement, holder.get());
    }


    @Test
    public void resultingV8Object_Of_OverriddenJavaInstanceInjection_ShouldHas_Fresh_ContentWhenReadBackInJava() {
        final V8JavaClassInterceptor interceptor = new V8JavaClassInterceptor<Map>() {
            @Override
            public Object objectInjectorOverride(Map object) {
                return V8ObjectUtils.toV8Object(v8, object);
            }

            @Override public String getConstructorScriptBody() { return null; }
            @Override public void onInject(V8JavaClassInterceptorContext context, Map object) { }
            @Override public void onExtract(V8JavaClassInterceptorContext context, Map object) { }
        };


        V8JavaAdapter.injectClass(HashMap.class, interceptor, v8);

        final Map<String, Object> mapToExportToJs = new HashMap<String, Object>();

        final String stringKey = "stringKey";
        final String stringValue = "stringValue";
        final String newStringValue = "stringValueNew";
        mapToExportToJs.put(stringKey, stringValue);

        final RawMapHolder mapProvider = new RawMapHolder();
        mapProvider.setMap(mapToExportToJs);

        final String mapProviderJsName = "mapProvider";
        V8JavaAdapter.injectObject(mapProviderJsName, mapProvider, v8);

        final String holderJsName = "holder";
        AtomicReference<Object> holder = new AtomicReference<Object>();
        V8JavaAdapter.injectObject(holderJsName, holder, v8);

        v8.executeVoidScript("var exportedMap = mapProvider.getMap(); exportedMap['" + stringKey + "'] = '" + newStringValue + "'; holder.set(exportedMap)");
        final Map mapReadBackFromV8Object = (Map) holder.get();
        Assert.assertNotEquals(mapToExportToJs, mapReadBackFromV8Object);
        Assert.assertEquals(newStringValue, mapReadBackFromV8Object.get("stringKey"));
    }
    @Test
    public void shouldHandleDefaultMethodsAndCall() {
    	if("1.8.0".compareTo(System.getProperty("java.version")) <= 0) {
    		//java 8+ test written in a java 7 compaditable way
    		LinkedList list = new LinkedList();
    		for(int i = 0;i < 10;i++) {
    			final int j = i;
    			list.add(
    					new Object() {//dummy object
    						private int hidden = j;
    						public void add() {
    							hidden++;
    						}
    						public String toString(){
    							return ""+hidden;
    						}
    					}
				);
    		}
    		//andThen would cause this to fail IF default methods aren't accounted for
    		V8JavaAdapter.injectObject("lists", list, v8);
    		//v8.executeScript("list.add('e');");
    		v8.executeScript("lists.forEach(function(e){e.add()});");
			for(int i = 0; i < list.size();i++) {
				Assert.assertEquals(""+(i+1),list.get(i).toString());
			}
    		
    	}
    	else {
    		//java 7- test
    		//TODO a good java 7 function test, I don't think its a thing .
    	}
    	
    }
    @Test
    public void shouldRunStaticMethodWithParams() {
    	//build a random string for test
    	String generatedString = "Hellow World";
    	
    	V8JavaAdapter.injectClass("StaticClass",StaticClass.class, v8);
    	v8.executeScript("function shouldRunStaticMethodWithParams(param){return StaticClass.paramsReturn(param);}");
    	v8.executeJSFunction("shouldRunStaticMethodWithParams", generatedString);
    	Assert.assertEquals(generatedString, v8.executeJSFunction("shouldRunStaticMethodWithParams", generatedString));
    }
    static class StaticClass{
    	public static <T> T paramsReturn(T obj) {
    		return obj;
    	}
    }
}