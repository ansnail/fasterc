package top.lib.fasterc.snapshot.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;

import top.lib.fasterc.snapshot.api.DiffInfo;
import top.lib.fasterc.snapshot.api.DiffResultSet;
import top.lib.fasterc.snapshot.api.Snapshot;


/**
 * 目录快照
 * Created by tong on 17/3/29.
 */
public class BaseDirectorySnapshot<DIFF_INFO extends FileDiffInfo,NODE extends FileNode> extends Snapshot<DIFF_INFO,NODE> {
    public String path;

    public BaseDirectorySnapshot() {
    }

    public BaseDirectorySnapshot(BaseDirectorySnapshot snapshoot) {
        super(snapshoot);
        this.path = snapshoot.path;
    }

    public BaseDirectorySnapshot(File directory) throws IOException {
        this(directory,(ScanFilter)null);
    }

    public BaseDirectorySnapshot(File directory, ScanFilter scanFilter) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
//        if (!directory.exists() || !directory.isDirectory()) {
//            throw new IllegalArgumentException("Invalid directory: " + directory);
//        }
        this.path = directory.getAbsolutePath();

        if (directory.exists() && directory.isDirectory()) {
            walkFileTree(directory,scanFilter);
        }
    }

    public BaseDirectorySnapshot(File directory, String ...childPath) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
//        if (!directory.exists() || !directory.isDirectory()) {
//            throw new IllegalArgumentException("Invalid directory: " + directory);
//        }
        this.path = directory.getAbsolutePath();

        if (childPath != null) {
            for (String path : childPath) {
                if (path != null) {
                    visitFile(new File(path).toPath(),null,null);
                }
            }
        }
    }

    public BaseDirectorySnapshot(File directory, Collection<File> childPath) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
//        if (!directory.exists() || !directory.isDirectory()) {
//            throw new IllegalArgumentException("Invalid directory: " + directory);
//        }

        this.path = directory.getAbsolutePath();

        if (childPath != null) {
            for (File f : childPath) {
                if (f != null) {
                    visitFile(f.toPath(),null,null);
                }
            }
        }
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new FileDiffInfo();
    }

    protected void walkFileTree(File directory, final ScanFilter scanFilter) throws IOException {
        Files.walkFileTree(directory.toPath(),new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return BaseDirectorySnapshot.this.visitFile(file,attrs,scanFilter);
            }
        });
    }

    protected FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs,ScanFilter scanFilter) throws IOException {
        if (scanFilter != null) {
            if (!scanFilter.preVisitFile(filePath.toFile())) {
                return FileVisitResult.CONTINUE;
            }
        }
        addNode((NODE) FileNode.create(new File(path),filePath.toFile()));
        return FileVisitResult.CONTINUE;
    }

    public File getAbsoluteFile(FileNode fileItemInfo) {
        return new File(path,fileItemInfo.getUniqueKey());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseDirectorySnapshot<?, ?> that = (BaseDirectorySnapshot<?, ?>) o;

        return path != null ? path.equals(that.path) : that.path == null;
    }



    @Override
    public int hashCode() {
        return path != null ? path.hashCode() : 0;
    }

    public static DiffResultSet<FileDiffInfo> diff(File now, File old) throws IOException {
        return BaseDirectorySnapshot.diff(now,old,null);
    }

    public static DiffResultSet<FileDiffInfo> diff(File now, File old, ScanFilter scanFilter) throws IOException {
        return  new BaseDirectorySnapshot(now,scanFilter).diff(new BaseDirectorySnapshot(old,scanFilter));
    }

    @Override
    public String toString() {
        return "BaseDirectorySnapshoot{" +
                "path='" + path + '\'' +
                '}';
    }
}
