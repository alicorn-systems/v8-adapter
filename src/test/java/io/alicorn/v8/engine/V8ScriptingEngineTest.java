package io.alicorn.v8.engine;

import org.junit.Assert;
import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class V8ScriptingEngineTest {
    private ScriptEngine engine;

    @Test
    public void shouldHaveACoolAPI() throws Exception {

        // Create the engine.
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("v8");
        Assert.assertNotNull(engine);

        // Perform basic evaluation.
        Assert.assertEquals(34, engine.eval("30 + 4"));

        // Perform advanced evaluation.
        Assert.assertEquals("l33t", engine.eval("\"l\" + (33).toString() + \"t\""));

        // Perform scope put-get.
        engine.put("myVar", 42);
        Assert.assertEquals(42, engine.get("myVar"));
    }
}