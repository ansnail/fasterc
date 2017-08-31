package top.lib.fasterc.snapshot.file;

import java.io.File;
import java.io.IOException;

/**
 * Created by tong on 17/3/29.
 */
public final class DirectorySnapshot extends BaseDirectorySnapshot<FileDiffInfo,FileNode> {
    public DirectorySnapshot() {
    }

    public DirectorySnapshot(BaseDirectorySnapshot snapshoot) {
        super(snapshoot);
    }

    public DirectorySnapshot(File directory) throws IOException {
        super(directory);
    }

    public DirectorySnapshot(File directory, ScanFilter scanFilter) throws IOException {
        super(directory, scanFilter);
    }
}
