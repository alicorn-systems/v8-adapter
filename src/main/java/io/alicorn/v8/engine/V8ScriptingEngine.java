package io.alicorn.v8.engine;

import com.eclipsesource.v8.V8;
import io.alicorn.v8.ConcurrentV8;
import io.alicorn.v8.ConcurrentV8Runnable;
import io.alicorn.v8.V8JavaAdapter;
import io.alicorn.v8.V8JavaObjectUtils;

import javax.script.*;
import java.io.IOException;
import java.io.Reader;

/**
 * V8 scripting engine backed by a {@link ConcurrentV8} instance for
 * use with server-side scripting.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class V8ScriptingEngine extends AbstractScriptEngine implements Invocable {
//Private//////////////////////////////////////////////////////////////////////

    // TODO: Should we always use a ConcurrentV8?
    private final ConcurrentV8 concurrentV8;
    private final V8ScriptingEngineFactory factory;

//Public///////////////////////////////////////////////////////////////////////

    public V8ScriptingEngine(V8ScriptingEngineFactory factory) {
        this.concurrentV8 = new ConcurrentV8();
        this.factory = factory;
    }

    @Override
    public Object eval(final String script, final ScriptContext context) throws ScriptException {
        try {
            final Object[] result = new Object[1];
            concurrentV8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) {

                    // TODO: Incorporate usage of the ScriptContext.
                    result[0] = v8.executeScript(script);
                }
            });
            return result[0];
        } catch (Exception e) {

            // TODO: Add checking to throw a NoSuchMethodException.
            throw new ScriptException(e);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        try {

            // Read in reader value
            int intValueOfChar;
            StringBuilder string = new StringBuilder();
            while ((intValueOfChar = reader.read()) != -1) {
                string.append((char) intValueOfChar);
            }
            reader.close();

            // Evaluate
            return eval(string.toString(), context);
        } catch (IOException e) {
            throw new ScriptException("Reader threw an IO exception while reading!");
        }
    }

    @Override
    public Bindings createBindings() {

        // TODO: Do we need to create any other kind of bindings?
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
        // TODO: Determine what exactly the "thiz" object would be an object of.
        return invokeFunction(name, args);
    }

    @Override
    public Object invokeFunction(final String name, final Object... args) throws ScriptException, NoSuchMethodException {
        try {
            final Object[] result = new Object[1];
            concurrentV8.run(new ConcurrentV8Runnable() {
                @Override
                public void run(V8 v8) {
                    result[0] = v8.executeJSFunction(name, V8JavaObjectUtils.translateJavaArgumentsToJavascript(args, v8, V8JavaAdapter.getCacheForRuntime(v8)));
                }
            });
            return result[0];
        } catch (Exception e) {

            // TODO: Add checking to throw a NoSuchMethodException.
            throw new ScriptException(e);
        }
    }

    @Override
    public <T> T getInterface(Class<T> clasz) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }
}
