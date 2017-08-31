package top.lib.fasterc.snapshot.sourceset;

import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import top.lib.fasterc.snapshot.api.DiffInfo;
import top.lib.fasterc.snapshot.api.DiffResultSet;
import top.lib.fasterc.snapshot.api.Snapshot;
import top.lib.fasterc.snapshot.api.Status;
import top.lib.fasterc.snapshot.file.FileNode;
import top.lib.fasterc.snapshot.string.BaseStringSnapshot;
import top.lib.fasterc.snapshot.string.StringDiffInfo;
import top.lib.fasterc.snapshot.string.StringNode;


/**
 * Created by tong on 17/3/31.
 */
public final class SourceSetSnapshot extends BaseStringSnapshot<StringDiffInfo,StringNode> {
    public String path;//工程目录

    @SerializedName("sourceSets")
    public Set<JavaDirectorySnapshot> directorySnapshootSet = new HashSet<>();

    public SourceSetSnapshot() {
    }

    public SourceSetSnapshot(SourceSetSnapshot snapshoot) {
        super(snapshoot);
        //from gson
        this.path = snapshoot.path;
        this.directorySnapshootSet.addAll(snapshoot.directorySnapshootSet);
    }

    public SourceSetSnapshot(File projectDir, Set<File> sourceSets) throws IOException {
        super(SourceSetSnapshot.getSourceSetStringArray(sourceSets));
        init(projectDir,sourceSets);
    }

    public SourceSetSnapshot(File projectDir, String ...sourceSets) throws IOException {
        super(sourceSets);

        Set<File> result = new HashSet<>();
        if (sourceSets != null) {
            for (String string : sourceSets) {
                result.add(new File(string));
            }
        }
        init(projectDir,result);
    }

    private void init(File projectDir,Set<File> sourceSetFiles) throws IOException {
        if (projectDir == null || projectDir.length() == 0) {
            throw new RuntimeException("Invalid projectPath");
        }
        this.path = projectDir.getAbsolutePath();
        if (directorySnapshootSet == null) {
            directorySnapshootSet = new HashSet<>();
        }

        if (sourceSetFiles != null) {
            for (File sourceSet : sourceSetFiles) {
                if (sourceSet != null) {
                    JavaDirectorySnapshot javaDirectorySnapshoot = new JavaDirectorySnapshot(sourceSet);
                    javaDirectorySnapshoot.projectPath = projectDir.getAbsolutePath();
                    directorySnapshootSet.add(javaDirectorySnapshoot);
                }
            }
        }
    }

    public void addJavaDirectorySnapshot(JavaDirectorySnapshot javaDirectorySnapshoot) {
        nodes.add(StringNode.create(javaDirectorySnapshoot.path));
        directorySnapshootSet.add(javaDirectorySnapshoot);
    }

    @Override
    protected SourceSetDiffResultSet createEmptyResultSet() {
        return new SourceSetDiffResultSet();
    }

    @Override
    public DiffResultSet<StringDiffInfo> diff(Snapshot<StringDiffInfo, StringNode> otherSnapshoot) {
        SourceSetDiffResultSet sourceSetResultSet = (SourceSetDiffResultSet) super.diff(otherSnapshoot);

        SourceSetSnapshot oldSnapshoot = (SourceSetSnapshot)otherSnapshoot;
        for (DiffInfo diffInfo : sourceSetResultSet.getDiffInfos(Status.DELETEED)) {
            JavaDirectorySnapshot javaDirectorySnapshoot = oldSnapshoot.getJavaDirectorySnapshootByPath(diffInfo.uniqueKey);

            JavaDirectoryDiffResultSet javaDirectoryDiffResultSet = javaDirectorySnapshoot.createEmptyResultSet();
            for (FileNode node : javaDirectorySnapshoot.nodes) {
                javaDirectoryDiffResultSet.add(new JavaFileDiffInfo(Status.DELETEED,null,node));
                //sourceSetResultSet.addJavaFileDiffInfo(new JavaFileDiffInfo(Status.DELETEED,null,node));
            }
            sourceSetResultSet.mergeJavaDirectoryResultSet(path,javaDirectoryDiffResultSet);
        }

        for (DiffInfo diffInfo : sourceSetResultSet.getDiffInfos(Status.ADDED)) {
            JavaDirectorySnapshot javaDirectorySnapshoot = getJavaDirectorySnapshootByPath(diffInfo.uniqueKey);

            JavaDirectoryDiffResultSet javaDirectoryDiffResultSet = javaDirectorySnapshoot.createEmptyResultSet();
            for (FileNode node : javaDirectorySnapshoot.nodes) {
                javaDirectoryDiffResultSet.add(new JavaFileDiffInfo(Status.ADDED,node,null));
                //sourceSetResultSet.addJavaFileDiffInfo(new JavaFileDiffInfo(Status.ADDED,node,null));
            }
            sourceSetResultSet.mergeJavaDirectoryResultSet(path,javaDirectoryDiffResultSet);
        }

        for (DiffInfo diffInfo : sourceSetResultSet.getDiffInfos(Status.NOCHANGED)) {
            JavaDirectorySnapshot now = getJavaDirectorySnapshootByPath(diffInfo.uniqueKey);
            JavaDirectorySnapshot old = oldSnapshoot.getJavaDirectorySnapshootByPath(diffInfo.uniqueKey);

            JavaDirectoryDiffResultSet resultSet = (JavaDirectoryDiffResultSet) now.diff(old);
            sourceSetResultSet.mergeJavaDirectoryResultSet(now.path,resultSet);
        }

        return sourceSetResultSet;
    }

    private JavaDirectorySnapshot getJavaDirectorySnapshootByPath(String path) {
        for (JavaDirectorySnapshot snapshoot : directorySnapshootSet) {
            if (snapshoot.path.equals(path)) {
                return snapshoot;
            }
        }
        return null;
    }

//    public void applyNewProjectDir(String oldRootProjectPath,String curRootProjectPath,String curProjectPath) {
//        this.path = curProjectPath;
//
//        for (StringNode node : nodes) {
//            node.setString(node.getString().replaceAll(oldRootProjectPath,curRootProjectPath));
//        }
//        for (JavaDirectorySnapshoot snapshoot : directorySnapshootSet) {
//            snapshoot.path = snapshoot.path.replaceAll(oldRootProjectPath,curRootProjectPath);
//            snapshoot.projectPath = snapshoot.projectPath.replaceAll(oldRootProjectPath,curRootProjectPath);
//        }
//    }

    @Override
    public String toString() {
        return "SourceSetSnapshoot{" +
                "path='" + path + '\'' +
                ", directorySnapshootSet=" + directorySnapshootSet +
                '}';
    }

    public static Set<String> getSourceSetStringArray(Set<File> sourceSets) {
        Set<String> result = new HashSet<>();
        if (sourceSets != null) {
            for (File file : sourceSets) {
                result.add(file.getAbsolutePath());
            }
        }
        return result;
    }
}
