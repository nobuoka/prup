package info.vividcode.develop.app.progen;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
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
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;

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
        Git destProjGit = Git.init().setDirectory(destPath.toFile()).call();

        Repository destRepo = destProjGit.getRepository();
        ObjectInserter inserter = destRepo.newObjectInserter();
        TreeFormatter formatter = new TreeFormatter();

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
                    // Git への add。
                    byte[] data = c.getBytes(StandardCharsets.UTF_8);
                    ObjectId fileId = inserter.insert(OBJ_BLOB, data, 0, data.length);
                    formatter.append(destFilePathStr, FileMode.REGULAR_FILE, fileId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        ObjectId treeId = inserter.insert(formatter);
        PersonIdent person = new PersonIdent("Your Name", "noreply@example.com");
        CommitBuilder commit = new CommitBuilder();
        commit.setTreeId(treeId);
        commit.setAuthor(person);
        commit.setCommitter(person);
        commit.setMessage("Initial Commit");
        ObjectId commitId = inserter.insert(commit);
        inserter.flush();

        RefUpdate ru = destRepo.updateRef(HEAD);
        ru.setForceUpdate(true);
        ru.setRefLogIdent(person);
        ru.setNewObjectId(commitId);
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setRefLogMessage("commit: Initial Commit", false);
        Result result = ru.update();

        inserter.release();

        destProjGit.close();
    }

}
