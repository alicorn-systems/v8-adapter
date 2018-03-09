package io.alicorn.v8;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8ScriptExecutionException;
import io.alicorn.v8.annotations.JSDisableMethodAutodetect;
import io.alicorn.v8.annotations.JSGetter;
import io.alicorn.v8.annotations.JSSetter;
import io.alicorn.v8.annotations.JSStaticFunction;
import org.hamcrest.core.StringContains;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.Random;

public class V8JavaAdapterTest {
//Setup classes////////////////////////////////////////////////////////////////
    private interface Baz {
        Foo doFooInterface(Foo foo);
    }

    private interface Bar {
        int doInterface(int args);
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
}