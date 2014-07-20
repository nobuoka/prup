package info.vividcode.develop.app.prup;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class GitFirstCommitBuilder implements Closeable {

    private final Git mTargetGit;
    private final ObjectInserter mInserter;

    // ,               , <TreeFormatter>
    // ,            aaa, <TreeFormatter>
    // aaa,         bbb, <TreeFormatter>
    // aaa/bbb,     ccc, <TreeFormatter>
    // aaa/bbb/ccc, ddd, <TreeFormatter>

    private final Table<String, String, TreeFormatter> mTreeFormatters = HashBasedTable.create();

    public static GitFirstCommitBuilder create() throws GitAPIException {
        Path destPath = Paths.get("test-dest-" + System.currentTimeMillis());
        Git destProjGit = Git.init().setDirectory(destPath.toFile()).call();
        return new GitFirstCommitBuilder(destProjGit);
    }

    private GitFirstCommitBuilder(Git targetGit) {
        mTargetGit = targetGit;
        Repository destRepo = mTargetGit.getRepository();
        mInserter = destRepo.newObjectInserter();
    }

    @Override
    public void close() {
        mTargetGit.close();
    }

    public void add(Path path, byte[] content) throws IOException {
        if (path.isAbsolute()) throw new RuntimeException("Must be relative path: " + path);
        ObjectId fileId = mInserter.insert(OBJ_BLOB, content, 0, content.length);

        Path parentOrNull = path.getParent();
        registerSelfAndAncestralDirectoriesTreeFormatters(parentOrNull);
        TreeFormatter tf = retrieveTreeFormatter(parentOrNull);
        tf.append(path.getFileName().toString(), FileMode.REGULAR_FILE, fileId);
    }

    private TreeFormatter retrieveTreeFormatter(Path pathOrNull) {
        if (pathOrNull == null) return mTreeFormatters.get("", "");
        Path self = pathOrNull.getFileName();
        String selfStr = (self != null ? self.toString() : "");
        Path parent = pathOrNull.getParent();
        String parentStr = (parent != null ? parent.toString() : "");
        return mTreeFormatters.get(parentStr, selfStr);
    }

    private Map<String, TreeFormatter> retrieveChildDirectoriesTreeFormatters(Path pathOrNull) {
        if (pathOrNull == null) return mTreeFormatters.row("");
        Map<String, TreeFormatter> columns = mTreeFormatters.row(pathOrNull.toString());
        if (pathOrNull.toString().equals("")) {
            columns.remove("");
        }
        return columns;
    }

    private void registerSelfAndAncestralDirectoriesTreeFormatters(Path pathOrNull) {
        Path parent = pathOrNull;
        while (parent != null) {
            Path self = parent.getFileName();
            String selfStr = (self != null ? self.toString() : "");
            parent = parent.getParent();
            String parentStr = (parent != null ? parent.toString() : "");
            System.out.println(parentStr + ", " + selfStr + "");
            // parent, self が既に登録されている場合は終了。
            TreeFormatter tf = mTreeFormatters.get(parentStr, selfStr);
            if (tf != null) break;
            // 登録されていない場合は登録。
            mTreeFormatters.put(parentStr, selfStr, new TreeFormatter());
        }
        String parentStr = "", selfStr = "";
        TreeFormatter tf = mTreeFormatters.get(parentStr, selfStr);
        if (tf == null) {
            mTreeFormatters.put(parentStr, selfStr, new TreeFormatter());
        }
    }

    private ObjectId fixTreeFormatter(Path path) throws IOException {
        TreeFormatter tf = retrieveTreeFormatter(path);
        Map<String, TreeFormatter> childTfs = retrieveChildDirectoriesTreeFormatters(path);
        for (String dirName : childTfs.keySet()) {
            ObjectId childTreeId = fixTreeFormatter(path.resolve(dirName));
            tf.append(dirName, FileMode.TREE, childTreeId);
        }
        return mInserter.insert(tf);
    }

    public void build() throws IOException, CheckoutConflictException, GitAPIException {
        ObjectId rootTreeId = fixTreeFormatter(Paths.get(""));

        String commitMsg = "First commit";

        PersonIdent person = new PersonIdent("Your Name", "noreply@example.com");
        CommitBuilder commit = new CommitBuilder();
        commit.setTreeId(rootTreeId);
        commit.setAuthor(person);
        commit.setCommitter(person);
        commit.setMessage(commitMsg);
        ObjectId commitId = mInserter.insert(commit);
        mInserter.flush();

        RefUpdate ru = mTargetGit.getRepository().updateRef(HEAD);
        ru.setForceUpdate(true);
        ru.setRefLogIdent(person);
        ru.setNewObjectId(commitId);
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setRefLogMessage("commit: " + commitMsg, false);
        ru.update(); // TODO: 結果確認。

        mInserter.release();

        mTargetGit.reset().setMode(ResetType.HARD).call();
    }

}
