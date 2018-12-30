package io.alicorn.v8;

import com.eclipsesource.v8.*;
import io.alicorn.v8.annotations.callback.JSListener;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Utilities for translating individual Java objects to and from V8.
 *
 * This class differs from {@link com.eclipsesource.v8.utils.V8ObjectUtils}
 * in that it bridges individual Java objects to and from a V8 runtime,
 * not entire lists or arrays of objects.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public final class V8JavaObjectUtils {
//Private//////////////////////////////////////////////////////////////////////

    /**
     * Super hax0r map used when comparing primitives and their boxed
     * counterparts.
     */
    private static final Map<Class<?>, Class<?>> BOXED_PRIMITIVE_MAP = new HashMap<Class<?>, Class<?>>() {
        @Override public Class<?> get(Object classy) {
            if (containsKey(classy)) {
                return super.get(classy);
            } else {
                return (Class<?>) classy;
            }
        }
    };

    static {
        BOXED_PRIMITIVE_MAP.put(boolean.class, Boolean.class);
        BOXED_PRIMITIVE_MAP.put(short.class, Short.class);
        BOXED_PRIMITIVE_MAP.put(int.class, Integer.class);
        BOXED_PRIMITIVE_MAP.put(long.class, Long.class);
        BOXED_PRIMITIVE_MAP.put(float.class, Float.class);
        BOXED_PRIMITIVE_MAP.put(double.class, Double.class);
    }

    /**
     * Returns true if the passed object is primitive in respect to V8.
     */
    private static boolean isBasicallyPrimitive(Object object) {
        return object instanceof V8Value ||
                object instanceof String ||
                object instanceof Boolean ||
                object instanceof Short ||
                object instanceof Integer ||
                object instanceof Long ||
                object instanceof Float ||
                object instanceof Double;
    }

    /**
     * Set of {@link V8Value}s held by this class or one of its delegates.
     *  If {@link #perV8GcExecutor} is set - the implementation is weak reference based and underlying
     *  V8 resources could be released.
     *  Otherwise resources are retained until {@link #releaseV8Resources(V8)} is called.
     *
     *  NOTE: V8Locker is used instead of V8 because .equals() and .hashCode() throws if v8 accessed from non-v8 thread (for unknown reason)
     */
    private static Map<Integer, Set<V8Value>> perV8Resources = new HashMap<Integer, Set<V8Value>>();

    /**
     *  If set for given V8 - {@link V8CallBackFunctionInvocationHandler} could be GCed when Proxy is not referenced
     *   in the client code and underlying V8Function could be released and memory could be freed.
     *
     *  NOTE: V8Locker is used instead of V8 because .equals() and .hashCode() throws if v8 accessed from non-v8 thread (for unknown reason)
     */
    private static Map <Integer, Executor> perV8GcExecutor = new HashMap<Integer, Executor>();

    /**
     * ID of V8, which can be obtained from any thread.
     *
     * Extra "V8 id" is used instead of V8 directly because .equals() and .hashCode() throws if v8 accessed from non-v8 thread (for unknown reason).
     *
     * TODO: Why?
     *
     * @param v8 V8 instance to get the unique V8 ID of.
     *
     * @return The ID of the given V8 instance.
     *
     */
    public static int getV8Id(V8 v8) {
        return v8.getLocker().hashCode();
    }

    private static Set<V8Value> getV8Resources(V8 v8) {
        final int v8Id = getV8Id(v8);

        if (!perV8Resources.containsKey(v8Id)) {
            initNewV8Resources(v8);
        }

        return perV8Resources.get(v8Id);
    }

    /**
     * Initializes v8 resources for track with empty set.
     *  If GC executor for current v8 is provided - the set is weak reference based.
     */
    private static void initNewV8Resources(V8 v8) {
        final Set<V8Value> v8Resources = newV8Resources(v8);
        perV8Resources.put(getV8Id(v8), v8Resources);
    }

    private static Set<V8Value> newV8Resources(V8 v8) {
        if (getGcExecutor(v8) == null) {
            return new HashSet<V8Value>();
        } else {
            return Collections.newSetFromMap(new WeakHashMap<V8Value, Boolean>());
        }
    }

    private static void removeV8Resources(V8 v8) {
        perV8Resources.remove(getV8Id(v8));
    }

    /**
     *  If set - {@link V8CallBackFunctionInvocationHandler} could be GCed when Proxy is not referenced
     *   in the client code and underlying V8Function could be released and memory could be freed.
     * @param v8
     */
    private static Executor getGcExecutor(V8 v8) {
        return perV8GcExecutor.get(getV8Id(v8));
    }

    /** package-private access for testing purposes only */
    static void removeGcExecutor(V8 v8) {
        perV8GcExecutor.remove(getV8Id(v8));
    }

    private static void setGcExecutorInner(V8 v8, Executor newGcExecutor) {
        perV8GcExecutor.put(getV8Id(v8), newGcExecutor);
    }

    /**
     * Lightweight adapter of JS Function.
     * Handles invocation of JS Function by handling translation of Java arguments to JS and return result back to Java.
     * It adapts certain V8 functions as Java functional class (abstract).
     *
     * Act as call-back, which could be invoked once, but not like listener.
     * If listener behaviour is desired -  {@link #onJsInvoked()} should be overwritten.
     */
    public abstract static class JsCallBackAdapter implements Releasable {
        private final V8JavaCache cache;
        private final V8Object receiver;
        private final V8Function function;

        public JsCallBackAdapter(V8Object receiver, V8Function function, V8JavaCache cache) {
            this.receiver = receiver.twin();
            this.function = function.twin();
            this.cache = cache;

            final Set<V8Value> v8Resources = getV8Resources();
            v8Resources.add(this.receiver);
            v8Resources.add(this.function);
            //optionally pass GC-Executor as argument (if accessing it as static field causes issues)
        }

        private Set<V8Value> getV8Resources() {
            return V8JavaObjectUtils.getV8Resources(receiver.getRuntime());
        }

        private Executor getGcExecutor() {
            return V8JavaObjectUtils.getGcExecutor(receiver.getRuntime());
        }

        /**
         * Releases underlying V8 resources. Must be called on V8 thread.
         */
        @Override public void release() {
            final Set<V8Value> v8Resources = getV8Resources();

            /*
             *  Note: check for isReleased() is required:
             *  otherwise .remove() invokes equals on v8 object, which throws if object is released.
             */
            if (!receiver.isReleased()) {
                v8Resources.remove(receiver);

                try {
                    receiver.release();
                } catch (Throwable t) {
                    logError("Unable to receiver.release()", t);
                }
            }

            if (!function.isReleased()) {
                v8Resources.remove(function);

                try {
                    function.release();
                } catch (Throwable t) {
                    logError("Unable to function.release()", t);
                }
            }
        }

        @Override protected void finalize() throws Throwable {
            try {
                final Executor gcExecutor = getGcExecutor();
                if (gcExecutor != null) {
                    gcExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            release();
                        }
                    });
                } else {
                    //Normally does nothing. But logs errors if .release() was nor called manually before.
                    release();
                }
            } finally {
                super.finalize();
            }
        }

        /**
         * By default V8 CallBack releases V8 resources when v8 function is invoked.
         * Child classes can change this behaviour.
         */
        protected void onJsInvoked() {
            release();
        }

        protected Object invokeJsFunction(Object[] javaArgs) {
            V8Array v8Args = translateJavaArgumentsToJavascript(javaArgs, getRuntimeSarcastically(receiver), cache);
            final Object obj;
            try {
                obj = function.call(receiver, v8Args);
            } finally {
                if (!v8Args.isReleased()) {
                    v8Args.release();
                }
            }

            try {
                onJsInvoked();
            } catch (Throwable t) {
                logError("Unable to perform onJsInvoked()", t);
            }

            if (obj instanceof V8Object) {
                V8Object v8Obj = ((V8Object) obj);
                if (!v8Obj.isUndefined()) {
                    Object ret = cache.identifierToJavaObjectMap.get(v8Obj.get(JAVA_OBJECT_HANDLE_ID).toString()).get();
                    v8Obj.release();
                    return ret;
                } else {
                    v8Obj.release();
                    return null;
                }
            } else {
                return obj;
            }
        }

        @Override public String toString() {
            return function.toString();
        }

        protected void logError(String logMessage, Throwable t) {
            System.err.println("[v8-adapter] " + logMessage + ": " + t + " .Thread + " + Thread.currentThread().getName());
        }
    }

    /**
     * Lightweight invocation handler for translating certain V8 functions to
     * Java functional interfaces.
     *
     * Act as call-back, which could be invoked once, but not like listener.
     */
    private static class V8CallBackFunctionInvocationHandler extends JsCallBackAdapter implements InvocationHandler {
        /**
         * Methods like .toString() or .release() should be invoked from the current class
         * (instead of sending to V8Function).
         */
        private static final List<String> ownMethodNames = new ArrayList<String>();

        static {
            for (Method ownMethod : JsCallBackAdapter.class.getDeclaredMethods()) {
                final String methodName = ownMethod.getName();
                if (!"invoke".equals(methodName)) ownMethodNames.add(methodName);
            }
        }

        public V8CallBackFunctionInvocationHandler(V8Object receiver, V8Function function, V8JavaCache cache) {
            super(receiver, function, cache);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (ownMethodNames.contains(method.getName())) {
                final Object result = method.invoke(this, args);
                return result;
            }

            final Object jsFunctionResult = invokeJsFunctionUsingMethodArgs(method, args);

            return jsFunctionResult;
        }

        @Override protected void onJsInvoked() {
            //the class can be called back in java 8 with forEach method
        }
        protected Object invokeJsFunctionUsingMethodArgs(Method method, Object[] args) throws Throwable {
            try {
                final boolean varArgsOnlyMethod = method.isVarArgs() && method.getParameterTypes().length == 1;
                final Object[] javaArgs;
                if (!varArgsOnlyMethod) {
                    javaArgs = args;
                } else {
                    //TODO: also consider "spreading" of var-args if there are another arguments before as for JS->Java case
                    javaArgs = (Object[]) args[0];
                }

                return invokeJsFunction(javaArgs);
            } catch (Throwable t) {
                throw t;
            }
        }
    }

    /**
     * @see JSListener
     */
    private static class V8ListenerFunctionInvocationHandler extends V8CallBackFunctionInvocationHandler {
        public V8ListenerFunctionInvocationHandler(V8Object receiver, V8Function function, V8JavaCache cache) {
            super(receiver, function, cache);
        }

        @Override protected void onJsInvoked() {
            //do not release v8 function: unlike base call-back class listener could be called multiple times.
        }
    }

    private static class DefaultJsBasedCallBack extends JsCallBackAdapter implements JsBasedCallBack {
        public DefaultJsBasedCallBack(V8Object receiver, V8Function function, V8JavaCache cache) {
            super(receiver, function, cache);
        }

        @Override
        public Object call(Object... args) {
            return invokeJsFunction(args);
        }
    }

//Public///////////////////////////////////////////////////////////////////////

    /**
     * Variable name used when attaching a Java object ID to a JS object.
     */
    public static final String JAVA_OBJECT_HANDLE_ID = "____JavaObjectHandleID____";

    /**
     * Variable name used when attaching an interceptor context to a JS object.
     */
    public static final String JAVA_CLASS_INTERCEPTOR_CONTEXT_HANDLE_ID = "____JavaClassInterceptorContextHandleID____";

    /**
     * Attempts to convert the given array into it's primitive counterpart.
     *
     * @param array Array to convert.
     * @param type Boxed type of the array to convert.
     *
     * @return Primitive version of the given array, or the original array
     *         if no primitive type matched the passed type.
     */
    public static Object toPrimitiveArray(Object[] array, Class<?> type) {
        if (Boolean.class.equals(type)) {
            boolean[] ret = new boolean[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Boolean) array[i];
            }
            return ret;
        } else if (Byte.class.equals(type)) {
            byte[] ret = new byte[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Byte) array[i];
            }
            return ret;
        } else if (Short.class.equals(type)) {
            short[] ret = new short[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Short) array[i];
            }
            return ret;
        } else if (Integer.class.equals(type)) {
            int[] ret = new int[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Integer) array[i];
            }
            return ret;
        } else if (Long.class.equals(type)) {
            long[] ret = new long[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Long) array[i];
            }
            return ret;
        } else if (Float.class.equals(type)) {
            float[] ret = new float[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Float) array[i];
            }
            return ret;
        } else if (Double.class.equals(type)) {
            double[] ret = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                ret[i] = (Double) array[i];
            }
            return ret;
        }

        return array;
    }

    /**
     * Attempts to widen a given number to work with the specified class.
     *
     * TODO: Surely there's a cleaner way to write this!
     *
     * @param <T> Type of the class to convert to.
     * @param from Number to widen.
     * @param to Class to widen to.
     *
     * @return A widened version of the passed number, or null if no
     *         possible solutions existed for widening.
     */
    @SuppressWarnings("unchecked")
    public static <T> T widenNumber(Object from, Class<T> to) {
        if (from.getClass().equals(to)) {
            return (T) from;
        }

        if (from instanceof Short) {
            if (to == Short.class || to == short.class) {
                return (T) from;
            } else if (to == Integer.class || to == int.class) {
                return (T) new Integer((Short) from);
            } else if (to == Long.class || to == long.class) {
                return (T) new Long((Short) from);
            } else if (to == Float.class || to == float.class) {
                return (T) new Float((Short) from);
            } else if (to == Double.class || to == double.class) {
                return (T) new Double((Short) from);
            }
        } else if (from instanceof Integer) {
            if (to == Integer.class || to == int.class) {
                return (T) from;
            } else if (to == Long.class || to == long.class) {
                return (T) new Long((Integer) from);
            } else if (to == Float.class || to == float.class) {
                return (T) new Float((Integer) from);
            } else if (to == Double.class || to == double.class) {
                return (T) new Double((Integer) from);
            }
        } else if (from instanceof Long) {
            if (to == Long.class || to == long.class) {
                return (T) from;
            } else if (to == Float.class || to == float.class) {
                return (T) new Float((Long) from);
            } else if (to == Double.class || to == double.class) {
                return (T) new Double((Long) from);
            }
        } else if (from instanceof Float) {
            if (to == Float.class || to == float.class) {
                return (T) from;
            } else if (to == Double.class || to == double.class) {
                return (T) new Double((Float) from);
            }
        } else if (from instanceof Double) {
            if (to == Double.class || to == double.class) {
                return (T) from;
            }
        }

        // Welp, find a default.
        throw new IllegalArgumentException("Primitive argument cannot be coerced to expected parameter type."
                + " Expected " + to + ", but actual is " + from.getClass() + " (value: " + from + ")");
    }

    /**
     * Ultimate hack method to work around a typo in the V8 libraries for
     * Android.
     *
     * TODO: Report upstream and stop using this dorky method.
     *
     * @param value V8Value to get a runtime from.
     *
     * @return The sarcastically obtained value.
     */
    public static final V8 getRuntimeSarcastically(V8Value value) {
        try {
            return value.getRuntime();
        } catch (Throwable t) {
            try {
                return (V8) value.getClass().getMethod("getRutime", new Class[0]).invoke(value);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     *  Sets V8 thread executor for GC purposes.
     *
     *  After this "Js function to Java Callback" can be GCed when there is no reference in the client code.
     *
     *   Otherwise underlying V8Function retains native memory until {@link #releaseV8Resources(V8)} is called.
     *
     * @param v8 V8 instance to set the GC executor for.
     * @param newGcExecutor New GC executor to use.
     *
     */
    public static void setGcExecutor(V8 v8, Executor newGcExecutor) {
      if (newGcExecutor == null) throw new IllegalArgumentException("Not null executor required");

      setGcExecutorInner(v8, newGcExecutor);

      makeV8ResourcesGcAble(v8);
    }

    /**
     * @return whether GC V8 executor is specified and related V8-based JS call-backs could be GCed and released.
     */
    private static boolean isGcExecutorSpecified(V8 v8) {
        return getGcExecutor(v8) != null;
    }

    /**
     * Change V8Resources set to be GC-able by using weak reference based implementation.
     * Should be enabled only when V8 executor is specified.
     */
    private static void makeV8ResourcesGcAble(V8 v8) {
      if (!isGcExecutorSpecified(v8)) throw new IllegalStateException("Gc executor must be set");

      //because it could be called from non v8 thread
      final Set<V8Value> existingV8Resources = getV8Resources(v8);

      //initialize with new weak Set
      initNewV8Resources(v8);
      getV8Resources(v8).addAll(existingV8Resources);
    }

    /**
     * Releases all V8 resources held by this class for a particular runtime.
     *
     * This method should only be called right before a V8 runtime is being
     * released, or else some resources created by this utility class will
     * fail to keep working.
     *
     * @param v8 V8 instance to release resources for.
     *
     * @return Number of resources that were released.
     */
    public static int releaseV8Resources(V8 v8) {
        int released = 0;

        // Remove resources across runtime.
        for (Iterator<V8Value> iterator = getV8Resources(v8).iterator(); iterator.hasNext();) {
            V8Value resource = iterator.next();
            iterator.remove();

            if (resource != null) {
                resource.release();
                released++;
            }
        }

        // Free any garbage collected classes.
        if (released > 0) {
            V8JavaAdapter.getCacheForRuntime(v8).removeGarbageCollectedJavaObjects();
        }

        removeV8Resources(v8);
        removeGcExecutor(v8);

        return released;
    }

    /**
     * Translates a single Java object into an equivalent V8Value.
     *
     * @param javaArgument Java argument to translate.
     * @param v8 V8 runtime that will be receiving the translated Java argument.
     * @param cache V8JavaCache associated with the given V8 runtime.
     *
     * @return Translated object.
     */
    public static Object translateJavaArgumentToJavascript(Object javaArgument, V8 v8, V8JavaCache cache) {
        if (javaArgument != null) {

            // Longs must be explicitly widened to the Double type because of JS's internal representation of numbers.
            if (javaArgument instanceof Long) {
                return ((Long) javaArgument).doubleValue();

            // Floats must be explicitly widened to the Double type because of JS's internal representation of numbers.
            } else if (javaArgument instanceof Float) {
                return ((Float) javaArgument).doubleValue();

            // Other primitives may be returned as-is.
            } else if (isBasicallyPrimitive(javaArgument)) {
                return javaArgument;

            // Objects must be bound to their JS objects.
            } else {
                String key = cache.v8ObjectToIdentifierMap.get(javaArgument);
                if (key != null) {
                    V8Object object = (V8Object) v8.get(key);
                    cache.cachedV8JavaClasses.get(javaArgument.getClass()).writeInjectedInterceptor(object);
                    return object;
                } else {
                    key = V8JavaAdapter.injectObject(null, javaArgument, v8);
                    return v8.get(key);
                }
            }
        }

        return null;
    }

    /**
     * Translates an array of Java arguments to a V8Array.
     *
     * @param javaArguments Java arguments to translate.
     * @param v8 V8 runtime that will be receiving the translated Java arguments.
     * @param cache V8JavaCache associated with the given V8 runtime.
     *
     * @return Translated array.
     */
    public static V8Array translateJavaArgumentsToJavascript(Object[] javaArguments, V8 v8, V8JavaCache cache) {
        V8Array v8Args = new V8Array(v8);
        for (Object argument : javaArguments) {
            if (argument == null) {
                v8Args.pushNull();
            } else if (argument instanceof V8Value) {
                v8Args.push((V8Value) argument);
            } else if (argument instanceof String) {
                v8Args.push((String) argument);
            } else if (argument instanceof Boolean) {
                v8Args.push((Boolean) argument);
            } else if (argument instanceof Short) {
                v8Args.push((Short) argument);
            } else if (argument instanceof Integer) {
                v8Args.push((Integer) argument);
            } else if (argument instanceof Long) {
                v8Args.push((Long) argument);
            } else if (argument instanceof Float) {
                v8Args.push((Float) argument);
            } else if (argument instanceof Double) {
                v8Args.push((Double) argument);
            } else {
                V8Value translatedJavaArgument = (V8Value) translateJavaArgumentToJavascript(argument, v8, cache);
                v8Args.push(translatedJavaArgument);
                translatedJavaArgument.release();
            }
        }

        return v8Args;
    }

    /**
     * Translates a single element from a V8Array to an Object based on a given Java argument type.
     *
     * It is the responsibility of the caller of this method to invoke {@link V8Value#release()} on
     * any objects passed to this method; this method will not make an effort to release them.
     *
     * @param javaArgumentType Java type that the argument must match.
     * @param argument Argument to translate to Java.
     * @param receiver V8Object receiver that any functional arguments should be tied to.
     * @param cache V8JavaCache associated with the given V8 runtime.
     * @param argGenericType Generic type of the javaArgumentType. Contains info about it actual Types arguments. Could be null.
     *
     * @return Translated Object based on the passed Java types and and Javascript value.
     *
     * @throws IllegalArgumentException if the Javascript value could not be coerced in the types
     *         specified by te passed array of java argument types.
     */
    public static Object translateJavascriptArgumentToJava(Class<?> javaArgumentType, Object argument, V8Object receiver, V8JavaCache cache, Type argGenericType) throws IllegalArgumentException {
        if (argument == null) return nullOrThrowOnPrimitive(javaArgumentType);

        if (argument instanceof V8Value) {
            if (argument instanceof V8Function) {
                final V8Function v8ArgumentFunction = (V8Function) argument;
                int methodsCount = 0;
                if(javaArgumentType.isInterface()) {
                    Method[] interfaceMethods = javaArgumentType.getMethods(); //we want the original methods, not the virtual methods
                    Method[] objectMethods = Object.class.getMethods();
                    for (int i = 0; i < interfaceMethods.length; i++) {
                    	boolean isDefault = false;
                    	// if(!interfaceMethods[i].isDefault()){ //java 8 or greater version
                        try {
                        	Method isDefaultCall = interfaceMethods[i].getClass().getMethod("isDefault");
							//if((isDefaultCall != null && (boolean)isDefaultCall.invoke(interfaceMethods[i]))){
	                    	if(interfaceMethods[i].isDefault()){ //java 8 or greater version
								isDefault = true;
							}
						} catch (Exception e) {
							// TODO maybe better error handling or just replace with java 1.8 version only
						}
                        if(!isDefault) {
							Breakable:
						    {
						        for (int j = 0; j < objectMethods.length; j++) {
						            if(objectMethods[j].equals(interfaceMethods[i])){
						                break Breakable;
						            }
						        }
						        methodsCount++;
						    }
	                    }
                    }
                }
                //TODO: update check in case of java 8 upgrade:
                if (methodsCount == 1) {
                    //Create a proxy class for the functional interface that wraps this V8Function.
                    final V8CallBackFunctionInvocationHandler handler;
                    ClassLoader loader = javaArgumentType.getClassLoader();
                    if (!javaArgumentType.isAnnotationPresent(JSListener.class)) {
                        handler = new V8CallBackFunctionInvocationHandler(receiver, v8ArgumentFunction, cache);
                    } else {
                        handler = new V8ListenerFunctionInvocationHandler(receiver, v8ArgumentFunction, cache);
                    }
                    if(loader != null) {//sometimes this method was thrown with default java interfaces. (Comsumer in list.forEach() for example)
                    	return Proxy.newProxyInstance(loader, new Class[] { javaArgumentType,Releasable.class}, handler);
                    }
                    else {
                        return Proxy.newProxyInstance(loader, new Class[] { javaArgumentType}, handler);
                    }
                } else if (V8Function.class == javaArgumentType) {
                    return v8ArgumentFunction.twin();
                } else if (Object.class == javaArgumentType && isGcExecutorSpecified(receiver.getRuntime())) {
                    return new DefaultJsBasedCallBack(receiver, v8ArgumentFunction, cache);
                } else {
                    throw new IllegalArgumentException(
                            "Method was passed V8Function but does not accept a functional interface: found " + javaArgumentType);
                }
            } else if (argument instanceof V8Array) {
                V8Array v8ArgumentArray = (V8Array) argument;

                if (javaArgumentType.isArray()) {
                    // Perform a single cast up front.

                    // TODO: This logic is almost identical to the varargs manipulation logic. Maybe we can reuse it?
                    final Class<?> originalArrayType = javaArgumentType.getComponentType();
                    Class<?> arrayType = originalArrayType;
                    if (BOXED_PRIMITIVE_MAP.containsKey(arrayType)) {
                        arrayType = BOXED_PRIMITIVE_MAP.get(arrayType);
                    }
                    Object[] array = convertToArray(v8ArgumentArray, arrayType, originalArrayType, receiver, cache);

                    if (BOXED_PRIMITIVE_MAP.containsKey(originalArrayType) && BOXED_PRIMITIVE_MAP.containsValue(arrayType)) {
                        return toPrimitiveArray(array, arrayType);
                    } else {
                        return array;
                    }
                } else if (List.class == javaArgumentType || Object.class == javaArgumentType) {


                    final Class<?> listType;
                    final List<Class> argGenericClassParams = getArgGenericClassParams(argGenericType);
                    if (!argGenericClassParams.isEmpty()) {
                        listType = argGenericClassParams.get(0);
                    } else {
                        listType = Object.class;
                    }

                    Object[] array = convertToArray(v8ArgumentArray, listType, listType, receiver, cache);

                    return Arrays.asList(array);
                } else if (V8Array.class == javaArgumentType) {
                    return v8ArgumentArray.twin();
                } else {
                    throw new IllegalArgumentException("Method was passed a V8Array but does not accept arrays.");
                }
            } else if (argument instanceof V8Object) {
                final V8Object v8ArgumentObject = (V8Object) argument;

                if (v8ArgumentObject.isUndefined()) return nullOrThrowOnPrimitive(javaArgumentType);

                try {
                    if (v8ArgumentObject.contains(JAVA_OBJECT_HANDLE_ID)) {
                        //Attempt to retrieve a Java object handle.
                        String javaHandle = (String) v8ArgumentObject.get(JAVA_OBJECT_HANDLE_ID);
                        Object javaObject = cache.identifierToJavaObjectMap.get(javaHandle).get();

                        if (javaArgumentType.isAssignableFrom(javaObject.getClass())) {
                            // Check if it's intercepted.
                            cache.cachedV8JavaClasses.get(javaObject.getClass()).readInjectedInterceptor(
                                    v8ArgumentObject);
                            return javaObject;
                        } else {
                            throw new IllegalArgumentException(
                                    "Argument is Java type but does not match signature for this method.");
                        }
                    } else if (Map.class == javaArgumentType || Object.class == javaArgumentType) {
                        final Class<?> mapValueType;
                        final List<Class> argGenericClassParams = getArgGenericClassParams(argGenericType);
                        if (argGenericClassParams.size() >= 2) {
                            mapValueType = argGenericClassParams.get(1);
                        } else {
                            mapValueType = Object.class;
                        }

                        return convertToMap(v8ArgumentObject, mapValueType, receiver, cache);
                    } else if (V8Object.class == javaArgumentType) {
                        return v8ArgumentObject.twin();
                    } else {
                        cache.removeGarbageCollectedJavaObjects();
                        throw new IllegalArgumentException(
                                "Argument has invalid Java object handle or object referenced by handle has aged out.");
                    }
                } catch (NullPointerException e) {
                    throw new IllegalArgumentException(
                            "Argument has invalid Java object handle or object referenced by handle has aged out.", e);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                            "Complex objects can only be passed to Java if they represent Java objects.", e);
                }
            } else {
                //TODO: Add support for arrays.
                throw new IllegalArgumentException(
                        "Translation of JS to Java arguments is only supported for primitives, objects, arrays and functions.");
            }
        } else {
            if (javaArgumentType.isAssignableFrom(argument.getClass()) ||
                    BOXED_PRIMITIVE_MAP.get(argument.getClass())
                            .isAssignableFrom(BOXED_PRIMITIVE_MAP.get(javaArgumentType))) {
                return argument;
            } else if (Number.class.isAssignableFrom(javaArgumentType) && argument instanceof Number) {
                    return widenNumber(argument, javaArgumentType);
            } else {
                throw new IllegalArgumentException("Incompatible parameter type."
                        + " Expected " + javaArgumentType
                        + ", but actual is " + argument.getClass() + " (value: " + argument + ")");

            }
        }
    }

    /**
     * @return class of the type param. E.g. List<String> => String or List<Map<Integer, String>> => Map.
     */
    private static List<Class> getArgGenericClassParams(Type argGenericType) {
        //if it's null or raw type without generic class information.
        if (!(argGenericType instanceof ParameterizedType)) return Collections.emptyList();

        final Type[] actualTypeArguments = ((ParameterizedType) argGenericType).getActualTypeArguments();
        List<Class> argumentGenericClassParams = new ArrayList<Class>(actualTypeArguments.length);

        for (Type typeArgument : actualTypeArguments) {
            if (typeArgument instanceof Class) {
                argumentGenericClassParams.add((Class) typeArgument);
            } else if (typeArgument instanceof ParameterizedType) {
                argumentGenericClassParams.add((Class) ((ParameterizedType) typeArgument).getRawType());
            } else {
                argumentGenericClassParams.add(Object.class);
            }
        }

        return argumentGenericClassParams;
    }

    public static Object translateJavascriptArgumentToJava(Class<?> javaArgumentType, Object argument, V8Object receiver, V8JavaCache cache) throws IllegalArgumentException {
        return translateJavascriptArgumentToJava(javaArgumentType, argument, receiver, cache, null);
    }

    private static Object nullOrThrowOnPrimitive(Class<?> javaArgumentType) {
        if (!javaArgumentType.isPrimitive()) {
            return null;
        } else {
            throw new IllegalArgumentException("Unable to convert null. Primitive expected: " + javaArgumentType);
        }
    }

    private static Object[] convertToArray(V8Array v8Array, Class<?> arrayType, Class<?> originalArrayType, V8Object receiver, V8JavaCache cache) {
        Object[] array = (Object[]) Array.newInstance(arrayType, v8Array.length());

        for (int i = 0; i < array.length; i++) {
            // We have to release the value immediately after using it if it's a V8Value.
            Object arrayElement = v8Array.get(i);
            try {
                //argGenericType could be read for GenericArrayType case (e.g. Map<Integer, String>[]), but omitted for simplicity
                array[i] = translateJavascriptArgumentToJava(originalArrayType, arrayElement, receiver, cache);
            } catch (IllegalArgumentException e) {
                throw e;
            } finally {
                if (arrayElement instanceof V8Value) {
                    ((V8Value) arrayElement).release();
                }
            }
        }
        return array;
    }


    private static Map<String, Object> convertToMap(V8Object v8Object, Class<?> valueType, V8Object receiver, V8JavaCache cache) {
        final LinkedHashMap<String, Object> javaMap = new LinkedHashMap<String, Object>();

        String[] keys = v8Object.getKeys();
        for (String key : keys) {
            final Object jsObjValue = v8Object.get(key);
            try {
                final Object translatedValue = translateJavascriptArgumentToJava(valueType, jsObjValue, receiver, cache);
                javaMap.put(key, translatedValue);
            } catch (IllegalArgumentException e) {
                throw e;
            } finally {
                if (jsObjValue instanceof V8Value) {
                    ((V8Value) jsObjValue).release();
                }
            }
        }

        return javaMap;
    }

    /**
     * Translates a V8Array of arguments to an Object array based on a set of Java argument types.
     *
     * @param isVarArgs Whether or not the Java parameters list ends in a varargs array.
     * @param javaArgumentTypes Java types that the arguments must match.
     * @param argsGenericType Generic types.
     * @param javascriptArguments Arguments to translate to Java.
     * @param receiver V8Object receiver that any functional arguments should be tied to.
     * @param cache V8JavaCache associated with the given V8 runtime.
     *
     * @return Translated Object array of arguments based on the passed Java types and V8Array.
     *
     * @throws IllegalArgumentException if the V8Array could not be coerced into the types specified
     *         by the passed array of Java argument types.
     */
    public static Object[] translateJavascriptArgumentsToJava(boolean isVarArgs, Class<?>[] javaArgumentTypes, Type[] argsGenericType, V8Array javascriptArguments, V8Object receiver, V8JavaCache cache) throws IllegalArgumentException {
        // Varargs handling.
        if (isVarArgs && javaArgumentTypes.length > 0 &&
                javaArgumentTypes[javaArgumentTypes.length - 1].isArray() &&
                javascriptArguments.length() >= javaArgumentTypes.length - 1) {

            Class<?> originalVarargsType = javaArgumentTypes[javaArgumentTypes.length - 1].getComponentType();
            Class<?> varargsType = originalVarargsType;
            if (BOXED_PRIMITIVE_MAP.containsKey(varargsType)) {
                varargsType = BOXED_PRIMITIVE_MAP.get(varargsType);
            }
            Object[] varargs = (Object[]) Array.newInstance(varargsType, javascriptArguments.length() - javaArgumentTypes.length + 1);
            Object[] returnedArgumentValues = new Object[javaArgumentTypes.length];

            for (int i = 0; i < javascriptArguments.length(); i++) {
                Object argument = javascriptArguments.get(i);

                try {
                    // If we haven't hit the varargs yet, insert normally.
                    if (returnedArgumentValues.length - 1 > i) {
                        returnedArgumentValues[i] =
                                translateJavascriptArgumentToJava(javaArgumentTypes[i],
                                        argument, receiver, cache, argsGenericType[i]);

                    // Otherwise insert into the varargs.
                    } else {
                        //argGenericType could be read for GenericArrayType case (e.g. Map<Integer, String>[]), but omitted for simplicity
                        varargs[i - (returnedArgumentValues.length - 1)] =
                                translateJavascriptArgumentToJava(varargsType, argument, receiver, cache);
                    }
                } catch (IllegalArgumentException e) {
                    throw e;
                } finally {
                    if (argument instanceof V8Value) {
                        ((V8Value) argument).release();
                    }
                }
            }

            // Convert any boxed primitives to actual primitives IF the original varargs type was a primitive..
            if (BOXED_PRIMITIVE_MAP.containsKey(originalVarargsType) && BOXED_PRIMITIVE_MAP.containsValue(varargsType)) {
                returnedArgumentValues[returnedArgumentValues.length - 1] = toPrimitiveArray(varargs, varargsType);
            } else {
                returnedArgumentValues[returnedArgumentValues.length - 1] = varargs;
            }

            return returnedArgumentValues;

        // Typical handling. Argument lengths must match exactly; Java does
        // not consistently support random null values being passed in to core libraries.
        } else if (javaArgumentTypes.length == javascriptArguments.length()) {
            Object[] returnedArgumentValues = new Object[javaArgumentTypes.length];

            for (int i = 0; i < javascriptArguments.length(); i++) {
                Object argument = javascriptArguments.get(i);
                try {
                    returnedArgumentValues[i] = translateJavascriptArgumentToJava(javaArgumentTypes[i], argument, receiver, cache, argsGenericType[i]);
                } catch (IllegalArgumentException e) {
                    throw e;
                } finally {
                    if (argument instanceof V8Value) {
                        ((V8Value) argument).release();
                    }
                }
            }

            return returnedArgumentValues;
        } else {
            throw new IllegalArgumentException(
                "Method arguments size and passed arguments size do not match. " +
                "Expected " + javaArgumentTypes.length + ", but got " + javascriptArguments.length());
        }
    }
}
