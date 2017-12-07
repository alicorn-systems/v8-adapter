package io.alicorn.v8.engine;

import com.eclipsesource.v8.V8;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory for creating {@link V8ScriptingEngine}s.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class V8ScriptingEngineFactory implements ScriptEngineFactory {
//Private//////////////////////////////////////////////////////////////////////

    private static final String ENGINE_NAME = "V8 Java Adapter Engine";
    private static final String ENGINE_VERSION = "0.1";

    private static final List<String> EXTENSIONS = Collections.unmodifiableList(
            Arrays.asList("js", "javascript", "es", "ecmascript"));

    private static final List<String> MIME_TYPES = Collections.unmodifiableList(
            Arrays.asList("application/javascript", "application/ecmascript", "text/javascript", "text/ecmascript"));

    private static final List<String> NAMES = Collections.unmodifiableList(
            Arrays.asList("V8", "v8", "javascript", "js", "ecmascript", "es"));

    private static final String LANGUAGE_NAME = "Javascript";

//Protected////////////////////////////////////////////////////////////////////

//Public///////////////////////////////////////////////////////////////////////

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getEngineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public List<String> getExtensions() {
        return EXTENSIONS;
    }

    @Override
    public List<String> getMimeTypes() {
        return MIME_TYPES;
    }

    @Override
    public List<String> getNames() {
        return NAMES;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getLanguageVersion() {
        return V8.getV8Version();
    }

    @Override
    public Object getParameter(String key) {
        if (key.equals(ScriptEngine.ENGINE)) {
            return getEngineName();
        } else if (key.equals(ScriptEngine.ENGINE_VERSION)) {
            return getEngineVersion();
        } else if (key.equals(ScriptEngine.LANGUAGE)) {
            return getLanguageName();
        } else if (key.equals(ScriptEngine.LANGUAGE_VERSION)) {
            return getLanguageVersion();
        } else if (key.equals(ScriptEngine.NAME)) {
            return getNames().get(0);
        } else if (key.equals("THREADING")) {

            // TODO:
            return "MULTITHREADED";
        }

        return null;
    }

    /**
     * @see ScriptEngineFactory#getMethodCallSyntax(String, String, String...)
     */
    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        StringBuilder ret = new StringBuilder(obj);
        ret.append(".").append(m).append("(");
        for (int i = 0; i < args.length; i++) {
            ret.append(args[i]);
            if (i < args.length - 1) {
                ret.append(",");
            }
        }
        ret.append(");");
        return ret.toString();
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        // TODO: Implement once a consistent output syntax is identified.
        // console.log is not present in ECMAScript / Pure JS.
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " Does not support generated output statements.");
    }

    @Override
    public String getProgram(String... statements) {
        StringBuilder sb = new StringBuilder();

        for (String statement : statements) {
            sb.append(statement).append(";").append("\n");
        }

        return sb.toString();
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new V8ScriptingEngine(this);
    }
}
