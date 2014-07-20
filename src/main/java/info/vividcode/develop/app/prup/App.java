package info.vividcode.develop.app.prup;

import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.script.ScriptException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

public class App {

    public static void main(String[] args) throws InstantiationException, ScriptException, IOException, InvalidRemoteException, TransportException, GitAPIException {
        // セキュリティポリシー
        //System.setProperty("java.security.policy", "java.policy");
        //System.setSecurityManager(new SecurityManager());

        String destDirPathStr = "dest-proj-" + System.currentTimeMillis();

        // 元となるプロジェクトの置き場所を生成。
        Path srcDirContainerPath = Files.createTempDirectory("prup");
        System.out.println(srcDirContainerPath);
        // 元となるプロジェクトを Git clone してくる。
        //Git srcGit = GitProcess.cloneRepo(srcDirContainerPath);
        Git srcGit = Git.open(Paths.get("src-repo").toFile());
        // ルール読み込み。
        String ruleScript = GitProcess.readRuleScript(srcGit.getRepository());
        Rule rule = new RuleProcessor().evalRuleScript(ruleScript);
        // 入力パラメータ取得。
        Map<String, String> params = inputParams(rule.paramNames);
        // パラメータ加工。
        rule.paramsUpdater.accept(params);
        // 新規プロジェクト生成。
        Path destDirPath = Files.createDirectory(Paths.get(destDirPathStr));
        generate(srcGit.getRepository(), destDirPath, params, rule.generationRule);
        // 元となるプロジェクトを削除。
        srcGit.close();
        Files.deleteIfExists(srcDirContainerPath);
    }

    private static Map<String, String> inputParams(Set<String> paramNames) {
        Map<String, String> params = new HashMap<>();
        Scanner scanner = new Scanner(System.in); // System.in を閉じたくないので close しない。
        for (String paramName : paramNames) {
            System.out.print(paramName + ": ");
            System.out.flush();
            String val = scanner.nextLine();
            params.put(paramName, val);
        }
        return params;
    }

    // コミットの作り方: https://gist.github.com/rkapsi/3298119
    private static void generate(Repository srcRepo, Path destPath, Map<String, String> params, Map<String, String> genRule)
            throws IOException, GitAPIException {
        try (GitFirstCommitBuilder b = GitFirstCommitBuilder.create()) {
            GitProcess.iterateTemplateFiles(srcRepo, (path, byteContent) -> {
                String targetPathTemplate = genRule.get(path);
                if (targetPathTemplate != null) {
                    SimpleTemplateEngine engine = new SimpleTemplateEngine();
                    try {
                        Template t;
                        t = engine.createTemplate(targetPathTemplate);
                        String destFilePathStr = t.make(params).toString();
                        String content = new String(byteContent, StandardCharsets.UTF_8);
                        t = engine.createTemplate(content);
                        String c = t.make(params).toString();
                        // Git リポジトリへの追加。
                        b.add(Paths.get(destFilePathStr), c.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            b.build();
        }
    }

}
