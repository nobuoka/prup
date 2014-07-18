package info.vividcode.develop.app.progen;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Rule {

    public final Set<String> paramNames;
    public final Consumer<Map<String, String>> paramsUpdater;
    public final Map<String, String> generationRule;

    public Rule(Set<String> paramNames, Consumer<Map<String, String>> paramsUpdater, Map<String, String> generationRule) {
        this.paramNames = paramNames;
        this.paramsUpdater = paramsUpdater;
        this.generationRule = generationRule;
    }

}
