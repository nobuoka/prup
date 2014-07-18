package info.vividcode.develop.app.progen;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.script.ScriptException;

class GeneratorRuleDefiner {
    private final Map<String, String> mGeneratorRule = new HashMap<>();
    public void define(String srcFilePath, String destFilePathTemplate) {
        mGeneratorRule.put(srcFilePath, destFilePathTemplate);
    }
    public Map<String, String> getGeneratorRule() {
        return mGeneratorRule;
    }
}

class GeneratorRuleDefiningClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final GeneratorRuleDefiner mDefiner;

    public GeneratorRuleDefiningClosure(GeneratorRuleDefiner definer) {
        super(null);
        mDefiner = definer;
    }

    public void doCall(Object[] args) {
        Closure<?> c = (Closure<?>) args[0];
        c.setDelegate(mDefiner);
        c.call();
    }
}

class InputParamsDefiner {
    private final Set<String> mParamNames = new HashSet<>();
    public void define(String name) {
        System.out.println("input param: " + name);
        mParamNames.add(name);
    }
    public Set<String> getDefinedParamNames() {
        return mParamNames;
    }
}

class InputParamsDefiningClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final InputParamsDefiner mDefiner;

    public InputParamsDefiningClosure(InputParamsDefiner definer) {
        super(null);
        mDefiner = definer;
    }

    public void doCall(Object[] args) {
        Closure<?> c = (Closure<?>) args[0];
        c.setDelegate(mDefiner);
        c.call();
    }
}

class ParamsUpdaterDefiner {
    private Closure<?> mClosure = null;
    public void setClosure(Closure<?> closure) {
        mClosure = closure;
    }
    public Consumer<Map<String, String>> getParamsUpdater() {
        return new Consumer<Map<String, String>>() {
            private final Closure<?> c = mClosure;
            @Override
            public void accept(Map<String, String> t) {
                if (c != null) c.call(t);
            }
        };
    }
}

class ParamsUpdaterDefiningClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final ParamsUpdaterDefiner mDefiner;

    public ParamsUpdaterDefiningClosure(ParamsUpdaterDefiner definer) {
        super(null);
        mDefiner = definer;
    }

    public void doCall(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Closure)) {
            throw new IllegalArgumentException("First argument must be Closure.");
        }
        mDefiner.setClosure((Closure<?>) args[0]);
    }
}

public class RuleProcessor {

    public void process(Path ruleFilePath) throws IOException, ScriptException {
        // Read.
        byte[] contentBytes = Files.readAllBytes(ruleFilePath);
        String rule = new String(contentBytes, StandardCharsets.UTF_8);
        evalRuleScript(rule);
    }

    public Rule evalRuleScript(String rule) throws ScriptException {
        InputParamsDefiner inputParamsDefiner = new InputParamsDefiner();
        ParamsUpdaterDefiner paramsUpdaterDefiner = new ParamsUpdaterDefiner();
        GeneratorRuleDefiner generatorRuleDefiner = new GeneratorRuleDefiner();
        // call groovy expressions from Java code
        Binding binding = new Binding();
        binding.setVariable("defineInputParams", new InputParamsDefiningClosure(inputParamsDefiner));
        binding.setVariable("defineParamsUpdater", new ParamsUpdaterDefiningClosure(paramsUpdaterDefiner));
        binding.setVariable("defineGeneratingRule", new GeneratorRuleDefiningClosure(generatorRuleDefiner));
        GroovyShell shell = new GroovyShell(binding);

        Object value = shell.evaluate(rule);
        System.out.println(value);

        System.out.println(inputParamsDefiner.getDefinedParamNames());
        return new Rule(
                inputParamsDefiner.getDefinedParamNames(),
                paramsUpdaterDefiner.getParamsUpdater(),
                generatorRuleDefiner.getGeneratorRule());
    }

}
