package io.alicorn.v8;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Centralized cache for resources created via the {@link V8JavaAdapter}. This class
 * is not meant to be used directly by API consumers; any actions should be performed
 * via the {@link V8JavaAdapter} class.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
final class V8JavaCache {
    /**
     * Cache of Java classes injected into V8 via the {@link V8JavaAdapter}.
     */
    final Map<Class<?>, V8JavaClassProxy> cachedV8JavaClasses = new HashMap<Class<?>, V8JavaClassProxy>();

    /**
     * Cache of Java objects created through V8 via a {@link V8JavaClassProxy}.
     */
    final Map<String, WeakReference> identifierToJavaObjectMap = new HashMap<String, WeakReference>();
    final Map<Object, String> v8ObjectToIdentifierMap = new WeakHashMap<Object, String>();

    /**
     * Removes any Java objects that have been garbage collected from the object cache.
     *
     * This method will invoke the Java garbage collector if objects are removed,
     * so it should only be invoked when a pause in program execution is acceptable.
     */
    public void removeGarbageCollectedJavaObjects() {
        int removed = 0;

        // Remove all nulled references from the V8 object map.
        Iterator<Map.Entry<String, WeakReference>> it = identifierToJavaObjectMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WeakReference> entry = it.next();
            if (entry.getValue().get() == null) {
                it.remove();

                // Increment number of removed objects.
                removed++;
            }
        }

        // If we removed more than one object, request a garbage collection now.
        if (removed > 0) {
            System.gc();
            System.runFinalization();
        }
    }
}
