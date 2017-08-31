package top.lib.fasterc.snapshot.sourceset;


import top.lib.fasterc.snapshot.api.DiffResultSet;

/**
 * Created by tong on 17/3/29.
 */
public class JavaDirectoryDiffResultSet extends DiffResultSet<JavaFileDiffInfo> {
    public String projectPath;

    public JavaDirectoryDiffResultSet() {
    }

    public JavaDirectoryDiffResultSet(JavaDirectoryDiffResultSet resultSet) {
        super(resultSet);
    }
}
