package info.vividcode.develop.app.prup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

public class GitProcess {

    public static Git cloneRepo(String cloneSource, Path cloneTarget) throws InvalidRemoteException, TransportException, GitAPIException {
        return Git.cloneRepository()
                .setURI(cloneSource)
                .setDirectory(cloneTarget.toFile())
                .setBare(true)
                .call();
    }

    public static String readRuleScript(Repository repo) throws IOException {
        String targetFilePath = "rule.groovy";

        Ref head = repo.getRef("refs/heads/master");
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        System.out.println(commit.getTree().getId().getName() + ":" + targetFilePath);
        ObjectId readmeFileObjId =
                repo.resolve(commit.getTree().getId().getName() + ":" + targetFilePath);
        ObjectLoader readmeFileObjLoader = repo.open(readmeFileObjId);
        byte[] ruleBytes = readmeFileObjLoader.getBytes();
        return new String(ruleBytes, StandardCharsets.UTF_8);
    }

    public static void run(Path gitRepoPath) throws IOException {
        File gitDir = gitRepoPath.toFile();
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(gitDir)
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        // the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
        Ref head = repository.getRef("refs/heads/master");
        System.out.println("Ref of refs/heads/master: " + head);

        System.out.println("Print contents of head of master branch, i.e. the latest commit information");
        ObjectLoader loader = repository.open(head.getObjectId());
        loader.copyTo(System.out);

        System.out.println("Print contents of tree of head of master branch, i.e. the latest binary tree information");

        // a commit points to a tree
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        RevTree tree = walk.parseTree(commit.getTree().getId());
        System.out.println("Found Tree: " + tree);
        loader = repository.open(tree.getId());
        loader.copyTo(System.out);
        System.out.println();

        ObjectId readmeFileObjId =
                repository.resolve(commit.getTree().getId().getName() + ":README.md");
        System.out.println(readmeFileObjId);
        ObjectLoader readmeFileObjLoader = repository.open(readmeFileObjId);
        readmeFileObjLoader.copyTo(System.out);

        TreeWalk treeWalk = new TreeWalk(repository);
        int treePos = treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        while (treeWalk.next()) {
            System.out.println("found: " + treeWalk.getPathString());
            ObjectId objId = treeWalk.getObjectId(treePos);
            loader = repository.open(objId);
            //loader.copyTo(System.out);
        }

        repository.close();
    }

    static final Pattern templatesFilePathPattern = Pattern.compile("\\Atemplates/(.+)\\z");

    public static void iterateTemplateFiles(Repository repo, BiConsumer<String, byte[]> proc) throws IOException {
        // the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
        Ref head = repo.getRef("refs/heads/master");

        // a commit points to a tree
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        RevTree tree = walk.parseTree(commit.getTree().getId());

        TreeWalk treeWalk = new TreeWalk(repo);
        int treePos = treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        while (treeWalk.next()) {
            System.out.println("found: " + treeWalk.getPathString());
            ObjectId objId = treeWalk.getObjectId(treePos);
            ObjectLoader loader = repo.open(objId);
            String pathStr = treeWalk.getPathString();
            Matcher m = templatesFilePathPattern.matcher(pathStr);
            if (m.matches()) {
                String p = m.group(1);
                proc.accept(p, loader.getBytes());
            }
        }

        repo.close();
    }

}
