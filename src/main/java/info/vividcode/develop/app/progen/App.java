package info.vividcode.develop.app.progen;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class App {
    public static void main(String[] args) throws InstantiationException, ScriptException, IOException, InvalidRemoteException, TransportException, GitAPIException {
        // セキュリティポリシー
        //System.setProperty("java.security.policy", "java.policy");
        //System.setSecurityManager(new SecurityManager());

        String destDirPathStr = "dist-proj";

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

        if (true) return;

        generateProject(Paths.get("src-repo"), Paths.get("test-repo"));

        Path gitRepoPath = Paths.get("cloned2");
        //GitProcess.cloneRepo(gitRepoPath);
        GitProcess.run(gitRepoPath);
        if (true) return;

        Collection<Permission> perms = new HashSet<Permission>();

        perms.add(new FilePermission("src-repo\\rule.js", "read"));

        ababa(perms).checkPermission(new FilePermission("src-repo\\rule.js", "read"));
        read();
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

    private static void generate(Repository srcRepo, Path destPath, Map<String, String> params, Map<String, String> genRule)
            throws IOException {
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
                    // 書きだし。
                    Path destFilePath = destPath.resolve(destFilePathStr);
                    Files.createDirectories(destFilePath.getParent());
                    Files.write(destFilePath, c.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public static void read() throws ScriptException, InstantiationException, IOException {
        Path srcDirPath = Paths.get("src-repo");
        Path ruleJsPath = srcDirPath.resolve("rule.js");
        String code = new String(Files.readAllBytes(ruleJsPath), StandardCharsets.UTF_8);
        System.out.println("" + evalWithNoPermission(code));
        System.exit(0);
    }


    public static Object evalWithNoPermission(String code) throws InstantiationException, ScriptException{
        String engineName = "Nashorn";
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine scriptEngine = sem.getEngineByName(engineName);
        if (scriptEngine == null){
            throw new InstantiationException("Could not load script engine: " + engineName);
        }
        scriptEngine.eval("function generate(def) { print(def) }");

        // See: http://worldwizards.blogspot.jp/2009/08/java-scripting-api-sandbox.html
        return AccessController.doPrivileged(new PrivilegedAction<Object>(){
            @Override
            public Object run() {
                try {
                    return scriptEngine.eval(code);
                } catch (ScriptException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return null;
            }
        }, ababa(null));
    }

    private static AccessControlContext ababa(Collection<Permission> permissionCollection) {
        Permissions perms = new Permissions();
        Enumeration<Permission> permElems = perms.elements();
        while (permElems.hasMoreElements()) {
            System.out.println(permElems.nextElement());
        }
        //perms.add(new RuntimePermission("accessDeclaredMembers"));
        if (permissionCollection != null){
            for (Permission p : permissionCollection){
                perms.add(p);
            }
        }
        // Cast to Certificate[] required because of ambiguity:
        ProtectionDomain domain = new ProtectionDomain(
                new CodeSource( null, (Certificate[]) null ), perms );
        return new AccessControlContext(new ProtectionDomain[] { domain } );
    }

    public static void ababa() {
        try {
            File gitDir = new File("test-repo/.git");
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir)
                      .readEnvironment() // scan environment GIT_* variables
                      .findGitDir() // scan up the file system tree
                      .build();
            repository.create();
            // ... use the new repository ...
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            // The repository already exists!
        } catch (IOException ex) {
            // Failed to create the repository!
            ex.printStackTrace();
        }
    }

    private static void generateProject(Path srcDirPath, Path destDirPath) {
        Path srcTemplatesDirPath = srcDirPath.resolve("templates");

        Path ruleFilePath = srcDirPath.resolve("rule.groovy");
        try {
            new RuleProcessor().process(ruleFilePath);
        } catch (IOException | ScriptException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        Map<String, String> params = new HashMap<>();
        Map<String, String> rule = new HashMap<>();
        rule.put("src/yourpackagename/Test.java", "src/info/vividcode/Test.java");
        rule.forEach((srcFilePathStr, destFilePathTemplateStr) -> {
            // TODO: テンプレート文字列を処理。
            String destFilePathStr = destFilePathTemplateStr;
            String fileContent;
            try {
                // TODO: テンプレート文字列を処理。
                fileContent = new String(Files.readAllBytes(srcTemplatesDirPath.resolve(srcFilePathStr)));
                // 書きこみ。
                Path destPath = destDirPath.resolve(destFilePathStr);
                Files.createDirectories(destPath.getParent());
                Files.write(destPath, fileContent.getBytes());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
