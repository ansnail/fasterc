package top.lib.fasterc.snapshot.sourceset;

import com.google.gson.annotations.Expose;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import top.lib.fasterc.snapshot.api.DiffInfo;
import top.lib.fasterc.snapshot.file.BaseDirectorySnapshot;
import top.lib.fasterc.snapshot.file.FileNode;
import top.lib.fasterc.snapshot.file.FileSuffixFilter;
import top.lib.fasterc.snapshot.file.ScanFilter;


/**
 * Created by tong on 17/3/30.
 */
public class JavaDirectorySnapshot extends BaseDirectorySnapshot<JavaFileDiffInfo,FileNode> {
    private static final FileSuffixFilter JAVA_SUFFIX_FILTER = new FileSuffixFilter(".java");
    @Expose
    public String projectPath;

    public JavaDirectorySnapshot() {
    }

    public JavaDirectorySnapshot(JavaDirectorySnapshot snapshoot) {
        super(snapshoot);
    }

    public JavaDirectorySnapshot(File directory) throws IOException {
        super(directory, JAVA_SUFFIX_FILTER);
    }

    public JavaDirectorySnapshot(File directory, ScanFilter scanFilter) throws IOException {
        super(directory, scanFilter);
    }

    public JavaDirectorySnapshot(File directory, String ...childPath) throws IOException {
        super(directory, childPath);
    }

    public JavaDirectorySnapshot(File directory, Collection<File> childPath) throws IOException {
        super(directory, childPath);
    }

    @Override
    protected JavaDirectoryDiffResultSet createEmptyResultSet() {
        JavaDirectoryDiffResultSet javaDirectoryDiffResultSet = new JavaDirectoryDiffResultSet();
        javaDirectoryDiffResultSet.projectPath = projectPath;
        return javaDirectoryDiffResultSet;
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new JavaFileDiffInfo();
    }
}
