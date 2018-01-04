package io.alicorn.v8;

import com.eclipsesource.v8.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Represents a proxy of a Java class for use within a V8 javascript context.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
final class V8JavaClassProxy implements JavaCallback {
//Private//////////////////////////////////////////////////////////////////////

    //Class represented by this proxy.
    private final Class<?> classy;
    private final V8JavaClassInterceptor interceptor;

    // Cache for the V8 runtime this proxy exists on.
    private final V8JavaCache cache;

    //Intercepted contexts owned by this proxy.
    private final Map<String, V8JavaClassInterceptorContext> interceptContexts = new HashMap<String, V8JavaClassInterceptorContext>();

    //Methods owned by this proxy.
    private final Map<String, V8JavaStaticMethodProxy> staticMethods = new HashMap<String, V8JavaStaticMethodProxy>();
    private final Map<String, V8JavaInstanceMethodProxy> instanceMethods = new HashMap<String, V8JavaInstanceMethodProxy>();

    // Getters and setters owned by this proxy.
    // These overlap with the methods defined above.
    private final List<String> gettersAndSettersList = new ArrayList<String>();
    private final Map<String, V8JavaInstanceMethodProxy> gettersMap = new HashMap<String, V8JavaInstanceMethodProxy>();
    private final Map<String, V8JavaInstanceMethodProxy> settersMap = new HashMap<String, V8JavaInstanceMethodProxy>();

    //Instances of this proxy created from JS. Used to control garbage collection.
//    private final List<Object> jsObjects = new ArrayList<Object>(); {
//        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
//            @Override public void run() {
//                // When the application exits, make sure no instances remained.
//                if (jsObjects.size() > 0) {
//                    System.err.println(jsObjects.size() + " instance(s) of " + classy.getName() +
//                                       " were created from JavaScript and not released via $release.");
//                }
//            }
//        }));
//    }

//Protected////////////////////////////////////////////////////////////////////

    /**
     * @return All static methods associated with the class this proxy represents.
     */
    List<V8JavaStaticMethodProxy> getStaticMethods() {
        return new ArrayList<V8JavaStaticMethodProxy>(staticMethods.values());
    }

//Public///////////////////////////////////////////////////////////////////////

    public V8JavaClassProxy(Class<?> classy, V8JavaClassInterceptor interceptor, V8JavaCache cache) {
        this.classy = classy;
        this.interceptor = interceptor;
        this.cache = cache;

        // TODO: Do we want to cache methods from non-final classes to reduce
        //       the memory footprint of multiple classes with a common base?

        // Keep track of getters and setters so we can register them at the end.
        List<V8JavaInstanceMethodProxy> gettersAndSetters = new ArrayList<V8JavaInstanceMethodProxy>();

        // Get all public methods for the given class.
        for (Method m : classy.getMethods()) {
            // We want to ignore any methods from the base object class for now since that
            // will take up excess memory for potentially unused features.
            if (!m.getDeclaringClass().equals(Object.class)) {

                // Register method.
                if (Modifier.isStatic(m.getModifiers())) {
                    if (staticMethods.containsKey(m.getName())) {
                        staticMethods.get(m.getName()).addMethodSignature(m);
                    } else {
                        V8JavaStaticMethodProxy methodProxy = new V8JavaStaticMethodProxy(m.getName(), cache);
                        methodProxy.addMethodSignature(m);
                        staticMethods.put(m.getName(), methodProxy);
                    }
                } else {
                    if (instanceMethods.containsKey(m.getName())) {
                        instanceMethods.get(m.getName()).addMethodSignature(m);
                    } else {
                        V8JavaInstanceMethodProxy methodProxy = new V8JavaInstanceMethodProxy(m.getName(), cache);
                        methodProxy.addMethodSignature(m);
                        instanceMethods.put(m.getName(), methodProxy);
                    }

                    // Determine if it's a getter or setter.
                    // TODO: Should this logic also process annotations?
                    if (m.getName().startsWith("get") || m.getName().startsWith("set")) {
                        gettersAndSetters.add(instanceMethods.get(m.getName()));
                    }
                }
            }
        }

        // Register all getters and setters.
        // TODO: Consider adding support for getter/setter generation being optional in order to reduce memory overhead?
        for (V8JavaInstanceMethodProxy method : gettersAndSetters) {
            // Disallow the special edge case where the method name is literally "get" or "set".
            if (method.getMethodName().equals("get") || method.getMethodName().equals("set")) {
                // TODO: This behavior could be modified to instead dynamically search for
                //       a property that's missing or undefined via the get/set functions.
                continue;
            } else {

                // Generate a new name with "get" or "set" removed.
                String name = method.getMethodName().substring(3);

                // Convert potential upper camel case to lower camel case.
                name = name.substring(0, 1).toLowerCase() + name.substring(1);

                // Add the method to the list if it isn't already there.
                if (!gettersAndSettersList.contains(name)) {
                    gettersAndSettersList.add(name);
                }

                // Add the method as a getter/
                if (method.getMethodName().startsWith("get")) {
                    gettersMap.put(name, method);

                // Add the method as a setter.
                } else if (method.getMethodName().startsWith("set")) {
                    settersMap.put(name, method);
                }
            }
        }
    }

    /**
     * Returns the {@link V8JavaClassInterceptor} associated with this class.
     *
     * @return The {@link V8JavaClassInterceptor} associated with this class,
     *         or null if none exists.
     */
    public V8JavaClassInterceptor getInterceptor() {
        return interceptor;
    }

    /**
     * Updates an {@link V8Object}'s state to match that of it's associated
     * {@link V8JavaClassInterceptor}.
     *
     * This method will do nothing if the specified V8Object does not have a
     * Java object handle or Java class interceptor handle.
     *
     * @param jsObject V8Object to restore from Java.
     */
    public void writeInjectedInterceptor(V8Object jsObject) {
        Object obj = jsObject.get(V8JavaObjectUtils.JAVA_CLASS_INTERCEPTOR_CONTEXT_HANDLE_ID);
        if (obj instanceof V8Value && ((V8Value) obj).isUndefined()) {
            ((V8Value) obj).release();
            return;
        }
        String interceptorAddress = String.valueOf(obj);

        obj = jsObject.get(V8JavaObjectUtils.JAVA_OBJECT_HANDLE_ID);
        if (obj instanceof V8Value && ((V8Value) obj).isUndefined()) {
            ((V8Value) obj).release();
            return;
        }
        String objectAddress = String.valueOf(obj);

        Object javaObject = cache.identifierToJavaObjectMap.get(objectAddress).get();
        V8JavaClassInterceptorContext context = interceptContexts.get(interceptorAddress);

        if (javaObject != null && context != null) {
            // Invoke the injection callback if present.
            Object function = jsObject.get("onJ2V8Inject");
            if (function instanceof V8Function) {
                // Despite being unchecked, we can guarantee that this is correct so long as the provided
                // interceptor is of the correct type. TODO: Maybe we could add an assert on the interceptor type?
                try {
                    interceptor.onInject(context, classy.cast(javaObject));
                } catch (Exception e) {
                    e.printStackTrace();
                    if (function instanceof V8Value) {
                        ((V8Value) function).release();
                    }
                    return;
                }
                V8Array args = V8JavaObjectUtils.translateJavaArgumentsToJavascript(new Object[] {context}, V8JavaObjectUtils.getRuntimeSarcastically(jsObject), cache);
                ((V8Function) function).call(jsObject, args);
                args.release();
            }

            // Clean up.
            if (function instanceof V8Value) {
                ((V8Value) function).release();
            }
        } else {
            System.err.println("omigod");
        }
    }

    /**
     * Restores the Java state of a {@link V8Object} that was intercepted
     * by an {@link V8JavaClassInterceptor}.
     *
     * This method will do nothing if the specified V8Object does not have a
     * Java object handle or Java class interceptor handle.
     *
     * @param jsObject V8Object to restore to Java.
     */
    public void readInjectedInterceptor(V8Object jsObject) {
        Object obj = jsObject.get(V8JavaObjectUtils.JAVA_CLASS_INTERCEPTOR_CONTEXT_HANDLE_ID);
        if (obj instanceof V8Value && ((V8Value) obj).isUndefined()) {
            ((V8Value) obj).release();
            return;
        }
        String interceptorAddress = String.valueOf(obj);

        obj = jsObject.get(V8JavaObjectUtils.JAVA_OBJECT_HANDLE_ID);
        if (obj instanceof V8Value && ((V8Value) obj).isUndefined()) {
            ((V8Value) obj).release();
            return;
        }
        String objectAddress = String.valueOf(obj);

        Object javaObject = cache.identifierToJavaObjectMap.get(objectAddress).get();
        V8JavaClassInterceptorContext context = interceptContexts.get(interceptorAddress);

        if (javaObject != null && context != null) {
            // Invoke the injection callback if present.
            Object function = jsObject.get("onJ2V8Extract");
            if (function instanceof V8Function) {
                V8Array args = V8JavaObjectUtils.translateJavaArgumentsToJavascript(new Object[] {context}, V8JavaObjectUtils.getRuntimeSarcastically(jsObject), cache);
                ((V8Function) function).call(jsObject, args);
                args.release();

                // Despite being unchecked, we can guarantee that this is correct so long as the provided
                // interceptor is of the correct type. TODO: Maybe we could add an assert on the interceptor type?
                try {
                    interceptor.onExtract(context, classy.cast(javaObject));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Clean up.
            if (function instanceof V8Value) {
                ((V8Value) function).release();
            }
        } else {
            System.err.println("omigod");
        }
    }

    /**
     * Attaches a Java object to a JS object, treating the JS object as if it
     * were a proxy for the Java object.
     *
     * @param javaObject Java object to attach.
     * @param jsObject JS object to attach to.
     *
     * @return String identifier for the final java script object.
     *
     * @throws IllegalArgumentException If the passed object is not an instance of the class this proxy represents.
     */
    public String attachJavaObjectToJsObject(Object javaObject, V8Object jsObject) throws IllegalArgumentException {
        if (javaObject.getClass().equals(classy)) {
            // Register its methods as properties on itself if it doesn't have an interceptor.
            if (interceptor == null) {

                // Register methods.
                for (String m : instanceMethods.keySet()) {
                    jsObject.registerJavaMethod(instanceMethods.get(m).getCallbackForInstance(javaObject), m);
                }

                // Register properties (getters and setters).
                for (String method : gettersAndSettersList) {

                    // Create a new JS object.
                    V8Object methodProperty = new V8Object(jsObject.getRuntime());

                    // Insert getter (if available).
                    if (gettersMap.containsKey(method)) {
                        methodProperty.registerJavaMethod(gettersMap.get(method).getCallbackForInstance(javaObject), "get");
                    }

                    // Insert setter (if available).
                    if (settersMap.containsKey(method)) {
                        methodProperty.registerJavaMethod(settersMap.get(method).getCallbackForInstance(javaObject), "set");
                    }

                    // Define property on JS object.
                    V8Object object = V8JavaObjectUtils.getRuntimeSarcastically(jsObject).getObject("Object");
                    V8Object ret = (V8Object) object.executeJSFunction("defineProperty", jsObject, method, methodProperty);

                    // Release garbage.
                    object.release();
                    ret.release();
                    methodProperty.release();
                }

            // Otherwise, register the interceptor's callback information.
            } else {
                String interceptorAddress = "CICHID" + UUID.randomUUID().toString().replaceAll("-", "");
                jsObject.add(V8JavaObjectUtils.JAVA_CLASS_INTERCEPTOR_CONTEXT_HANDLE_ID, interceptorAddress);
                V8JavaClassInterceptorContext context = new V8JavaClassInterceptorContext();
                interceptContexts.put(interceptorAddress, context);

                // Invoke the injection callback if present.
                Object function = jsObject.get("onJ2V8Inject");
                if (function instanceof V8Function) {
                    // Despite being unchecked, we can guarantee that this is correct so long as the provided
                    // interceptor is of the correct type. TODO: Maybe we could add an assert on the interceptor type?
                    interceptor.onInject(context, classy.cast(javaObject));
                    V8Array args = V8JavaObjectUtils.translateJavaArgumentsToJavascript(new Object[] {context}, V8JavaObjectUtils.getRuntimeSarcastically(jsObject), cache);
                    ((V8Function) function).call(jsObject, args);
                    args.release();
                }

                // Clean up.
                if (function instanceof V8Value) {
                    ((V8Value) function).release();
                }
            }

            //Register the object's handle.
            String instanceAddress = "OHID" + UUID.randomUUID().toString().replaceAll("-", "");
            jsObject.add(V8JavaObjectUtils.JAVA_OBJECT_HANDLE_ID, instanceAddress);
            WeakReference<Object> reference = new WeakReference<Object>(javaObject);
            cache.identifierToJavaObjectMap.put(instanceAddress, reference);
            cache.v8ObjectToIdentifierMap.put(javaObject, instanceAddress);

            //Add a handle to the object on the V8 context.
            V8JavaObjectUtils.getRuntimeSarcastically(jsObject).add(instanceAddress, jsObject);

            return instanceAddress;
        } else {
            throw new IllegalArgumentException(String.format("Cannot attach Java object of type [%s] using proxy for type [%s]",
                                                             javaObject.getClass().getName(), classy.getName()));
        }
    }

    /**
     * Creates a new Java object representing the type associated with this proxy.
     *
     * @param receiver Java Script object that will represent the Java object.
     * @param parameters Parameters to use when constructing the Java object.
     */
    @Override public Object invoke(V8Object receiver, V8Array parameters) {
        //Attempt to discover a matching constructor for the arguments we've been passed.
        Object[] coercedArguments = null;
        Constructor coercedConstructor = null;
        for (Constructor constructor : classy.getConstructors()) {
            try {
                coercedArguments = V8JavaObjectUtils.translateJavascriptArgumentsToJava(constructor.isVarArgs(), constructor.getParameterTypes(), parameters, receiver, cache);
                coercedConstructor = constructor;
                break;
            } catch (IllegalArgumentException e) {

            }
        }

        if (coercedArguments == null) {
            throw new IllegalArgumentException("No constructor exists for " + classy.getName() + " with specified arguments.");
        }

        try {
            final Object instance = coercedConstructor.newInstance(coercedArguments);
            attachJavaObjectToJsObject(instance, receiver);

            // TODO: Is this the best way to handle cleanup of Java objects for garbage collection?
            // Give it the ability to release itself.
//            jsObjects.add(instance);
//            receiver.registerJavaMethod(new JavaCallback() {
//                @Override public Object invoke(V8Object receiver, V8Array parameters) {
//                    // Dispose of any references to allow for garbage collection.
//                    jsObjects.remove(instance);
//                    return receiver;
//                }
//            }, "$release");
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Constructor received invalid arguments!");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Constructor received invalid arguments!");
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Constructor received invalid arguments!");
        }

        return null;
    }
}