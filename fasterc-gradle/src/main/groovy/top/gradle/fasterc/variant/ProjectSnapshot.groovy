package top.gradle.fasterc.variant

import org.gradle.api.Project
import top.gradle.fasterc.utils.FasterCUtils;
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.utils.GradleUtils
import top.lib.fasterc.snapshot.sourceset.JavaDirectorySnapshot
import top.lib.fasterc.snapshot.sourceset.SourceSetDiffResultSet
import top.lib.fasterc.snapshot.sourceset.SourceSetSnapshot
import top.lib.fasterc.snapshot.string.StringNode
import top.lib.fasterc.snapshot.string.StringSnapshot;
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.SerializeUtils

/**
 * Created by yanjie on 2017-08-16.
 * Describe:工程快照
 */

public class ProjectSnapshot {
    FasterCVariant fasterCVariant
    SourceSetSnapshot sourceSetSnapshot
    SourceSetSnapshot oldSourceSetSnapshot
    SourceSetDiffResultSet diffResultSet
    SourceSetDiffResultSet oldDiffResultSet
    StringSnapshot dependenciesSnapshot
    StringSnapshot oldDependenciesSnapshot

    ProjectSnapshot(FasterCVariant fasterCVariant) {
        this.fasterCVariant = fasterCVariant
    }

    def loadSnapshot() {
        if (!fasterCVariant.hasDexCache) {
            return
        }
        def project = fasterCVariant.project
        //读取保存过的快照文件
        File sourceSetSnapshotFile = FasterCUtils.getSourceSetSnapshotFile(project,fasterCVariant.variantName)
        oldSourceSetSnapshot = SourceSetSnapshot.load(sourceSetSnapshotFile,SourceSetSnapshot.class)
        //读取保存过的依赖列表文件
        File dependenciesListFile = FasterCUtils.getCachedDependListFile(project,fasterCVariant.variantName)
        oldDependenciesSnapshot = StringSnapshot.load(dependenciesListFile,StringSnapshot.class)
        //保存的工程路径
        String oldProjectPath = fasterCVariant.metaInfo.projectPath
        String curProjectPath = project.projectDir.absolutePath

        String oldRotProjectPath = fasterCVariant.metaInfo.rotProjectPath
        String curRotProjectPath = project.rotProject.projectDir.absolutePath

        def isRootProjectDirChanged = fasterCVariant.metaInfo.isRootProjectDirChanged(curRotProjectPath)
        if (isRootProjectDirChanged) {
            //已存在构建缓存的情况下,如果移动了项目目录要把缓存中的老的路径全部替换掉
            //更改路径并保存快照信息
            applyNewProjectDir(oldSourceSetSnapshot,oldRotProjectPath,curRotProjectPath,curProjectPath)
            if (oldSourceSetSnapshot.lastDiffResult != null) {
                oldSourceSetSnapshot.lastDiffResult = null
            }
            saveSourceSetSnapshot(oldSourceSetSnapshot)
            //更改路径并保存依赖信息
            for (StringNode node : oldDependenciesSnapshot.nodes) {
                node.string = replacePath(node.string,oldRotProjectPath,curRotProjectPath)
            }
            saveDependenciesSnapshot(oldDependenciesSnapshot)

            //保存全局编译信息
            fasterCVariant.metaInfo.projectPath = curProjectPath
            fasterCVariant.metaInfo.rotProjectPath = curRotProjectPath
            fasterCVariant.saveMetaInfo()
            FasterCLog.error(project,"fasterc restore cache, project path changed old: ${oldProjectPath} now: ${curProjectPath}")
        }
    }


    def applyNewProjectDir(SourceSetSnapshot sourceSnapshot,String oldRotProjectPath,String curRotProjectPath,String curProjectPath) {
        sourceSnapshot.path = curProjectPath
        for (StringNode node : sourceSnapshot.nodes) {
            node.setString(replacePath(node.getString(),oldRotProjectPath,curRotProjectPath))
        }
        for (JavaDirectorySnapshot snapshot : sourceSnapshot.directorySnapshotSet) {
            snapshot.path = replacePath(snapshot.path,oldRotProjectPath,curRotProjectPath)
            snapshot.projectPath = replacePath(snapshot.projectPath,oldRotProjectPath,curRotProjectPath)
        }
    }

    def replacePath(String path, String s, String s1) {
        if (path.startsWith(s)) {
            path = path.substring(s.length());
            path = s1 + path;
        }
        return path;
    }

    def prepareEnv() {
        def project = fasterCVariant.project
        sourceSetSnapshot = new SourceSetSnapshot(project.projectDir,getProjectSrcDirSet(project))
        handleGeneratedSource(sourceSetSnapshot)
        handleLibraryDependencies(sourceSetSnapshot)

        if (fasterCVariant.hasDexCache) {
            diffResultSet = sourceSetSnapshot.diff(oldSourceSetSnapshot)
            if (!fasterCVariant.firstPatchBuild) {
                File diffResultSetFile = FasterCUtils.getDiffResultSetFile(project,fasterCVariant.variantName)
                oldDiffResultSet = SourceSetDiffResultSet.load(diffResultSetFile,SourceSetDiffResultSet.class)
            }
        }
    }

    /**
     * 把自动生成的代码添加到源码快照中(R.java、buildConfig.java)
     * @param snapshot
     */
    def handleGeneratedSource(SourceSetSnapshot snapshot) {
        List<LibDependency> androidLibDependencies = new ArrayList<>()
        for (LibDependency libDependency : fasterCVariant.libraryDependencies) {
            if (libDependency.androidLibrary) {
                androidLibDependencies.add(libDependency)
            }
        }

        //File rDir = new File(fasterCVariant.project.buildDir,"generated${File.separator}source${File.separator}r${File.separator}${fasterCVariant.androidVariant.dirName}${File.separator}")
        File rDir = fasterCVariant.androidVariant.getVariantData().getScope().getRClassSourceOutputDir()
        //r
        JavaDirectorySnapshot rSnapshot = new JavaDirectorySnapshot(rDir,getAllRjavaPath(fasterCVariant.project,androidLibDependencies))
        rSnapshot.projectPath = fasterCVariant.project.projectDir.absolutePath
        snapshot.addJavaDirectorySnapshot(rSnapshot)

        //buildconfig
        List<Project> projectList = new ArrayList<>()
        projectList.add(fasterCVariant.project)
        for (LibDependency libDependency : androidLibDependencies) {
            projectList.add(libDependency.dependencyProject)
        }

        String buildTypeName = fasterCVariant.androidVariant.getBuildType().buildType.getName()
        String dirName = fasterCVariant.androidVariant.dirName
        def libraryVariantdirName = ShareConstants.DEFAULT_LIBRARY_VARIANT_DIR_NAME
        if (dirName != buildTypeName) {
            libraryVariantdirName = dirName.substring(0,dirName.length() - buildTypeName.length())
            libraryVariantdirName = "${libraryVariantdirName}${Constants.DEFAULT_LIBRARY_VARIANT_DIR_NAME}"

            if (libraryVariantdirName.startsWith(File.separator)) {
                libraryVariantdirName = libraryVariantdirName.substring(1)
            }
            if (libraryVariantdirName.endsWith(File.separator)) {
                libraryVariantdirName = libraryVariantdirName.substring(0,libraryVariantdirName.length() - 1)
            }
        }
        for (int i = 0;i < projectList.size();i++) {
            Project project = projectList.get(i)
            String packageName = GradleUtils.getPackageName(project.android.sourceSets.main.manifest.srcFile.absolutePath)
            String packageNamePath = packageName.split("\\.").join(File.separator)
            //buildconfig
            String buildConfigJavaRelativePath = "${packageNamePath}${File.separator}BuildConfig.java"
            File buildConfigDir = null
            if (i == 0) {
                buildConfigDir = fasterCVariant.androidVariant.getVariantData().getScope().getBuildConfigSourceOutputDir()
            }
            else {
                buildConfigDir = new File(project.buildDir,"generated${File.separator}source${File.separator}buildConfig${File.separator}${libraryVariantdirName}${File.separator}")
            }
            File buildConfigJavaFile = new File(buildConfigDir,buildConfigJavaRelativePath)
            if (fasterCVariant.configuration.debug) {
                FasterCLog.error(project,"fasterc buildConfigJavaFile: ${buildConfigJavaFile}")
            }
            JavaDirectorySnapshot buildConfigSnapshot = new JavaDirectorySnapshot(buildConfigDir,buildConfigJavaFile.absolutePath)
            buildConfigSnapshot.projectPath = project.projectDir.absolutePath
            snapshot.addJavaDirectorySnapshot(buildConfigSnapshot)
        }
    }

    /**
     * 往源码快照里添加依赖的工程源码路径
     * @param snapshot
     */
    def handleLibraryDependencies(SourceSetSnapshot snapshot) {
        for (LibDependency libDependency : fasterCVariant.libraryDependencies) {
            Set<File> srcDirSet = getProjectSrcDirSet(libDependency.dependencyProject)

            for (File file : srcDirSet) {
                JavaDirectorySnapshot javaDirectorySnapshot = new JavaDirectorySnapshot(file)
                javaDirectorySnapshot.projectPath = libDependency.dependencyProject.projectDir.absolutePath
                snapshot.addJavaDirectorySnapshot(javaDirectorySnapshot)
            }
        }
    }

    /**
     * 获取application工程自身和依赖的aar工程的所有R文件相对路径
     * @param appProject
     * @param androidLibDependencies
     * @return
     */
    def getAllRjavaPath(Project appProject,List<LibDependency> androidLibDependencies) {
        File rDir = new File(appProject.buildDir,"generated${File.separator}source${File.separator}r${File.separator}${fasterCVariant.androidVariant.dirName}${File.separator}")
        List<File> fileList = new ArrayList<>()
        for (LibDependency libDependency : androidLibDependencies) {
            String packageName = GradleUtils.getPackageName(libDependency.dependencyProject.android.sourceSets.main.manifest.srcFile.absolutePath)
            String packageNamePath = packageName.split("\\.").join(File.separator)

            String rjavaRelativePath = "${packageNamePath}${File.separator}R.java"
            File rjavaFile = new File(rDir,rjavaRelativePath)
            fileList.add(rjavaFile)

            if (fasterCVariant.configuration.debug) {
                fasterCVariant.FasterCLog.error(project,"fasterc rjavaFile: ${rjavaFile}")
            }
        }
        return fileList
    }

    /**
     * 获取工程对应的所有源码目录
     * @param project
     * @return
     */
    def getProjectSrcDirSet(Project project) {
        def srcDirs = new LinkedHashSet()
        if (project.hasProperty("android") && project.android.hasProperty("sourceSets")) {
            srcDirs.addAll(FasterCUtils.getSrcDirs(project,"main"))
            String buildTypeName = fasterCVariant.androidVariant.getBuildType().buildType.getName()
            String flavorName = fasterCVariant.androidVariant.flavorName
            if (buildTypeName && flavorName) {
                srcDirs.addAll(FasterCUtils.getSrcDirs(project,flavorName + buildTypeName.capitalize() as String))
            }
            if (buildTypeName) {
                srcDirs.addAll(FasterCUtils.getSrcDirs(project,buildTypeName))
            }
            if (flavorName) {
                srcDirs.addAll(FasterCUtils.getSrcDirs(project,flavorName))
            }
        }
        else if (project.plugins.hasPlugin("java") && project.hasProperty("sourceSets")) {
            srcDirs.addAll(project.sourceSets.main.java.srcDirs.asList())
        }
        if (fasterCVariant.configuration.debug) {
            FasterCLog.error(project,"fasterc: sourceSets ${srcDirs}")
        }
        Set<File> srcDirSet = new LinkedHashSet<>()
        if (srcDirs != null) {
            for (Object src : srcDirs) {
                if (src instanceof File) {
                    srcDirSet.add(src)
                }
                else if (src instanceof String) {
                    srcDirSet.add(new File(src))
                }
            }
        }
        return srcDirSet
    }

    /**
     * 保存源码快照信息
     * @param snapshot
     * @return
     */
    def saveSourceSetSnapshot(SourceSetSnapshot snapshot) {
        snapshot.serializeTo(new FileOutputStream(FasterCUtils.getSourceSetSnapshotFile(fasterCVariant.project,fasterCVariant.variantName)))
    }

    /**
     * 保存当前的源码快照信息
     * @return
     */
    def saveCurrentSourceSetSnapshot() {
        saveSourceSetSnapshot(sourceSetSnapshot)
    }

    /**
     * 保存源码对比结果
     * @return
     */
    def saveDiffResultSet() {
        if (diffResultSet != null && !diffResultSet.changedJavaFileDiffInfos.empty) {
            File diffResultSetFile = FasterCUtils.getDiffResultSetFile(fasterCVariant.project,fasterCVariant.variantName)
            //全量打包后首次java文件发生变化
            diffResultSet.serializeTo(new FileOutputStream(diffResultSetFile))
        }
    }

    /**
     * 删除源码对比结果
     * @return
     */
    def deleteLastDiffResultSet() {
        File diffResultSetFile = FasterCUtils.getDiffResultSetFile(fasterCVariant.project,fasterCVariant.variantName)
        FileUtils.deleteFile(diffResultSetFile)
    }

    /**
     * 依赖列表是否发生变化
     * @return
     */
    def isDependenciesChanged() {
        if (dependenciesSnapshot == null) {
            dependenciesSnapshot = new StringSnapshot(GradleUtils.getCurrentDependList(fasterCVariant.project,fasterCVariant.androidVariant))
        }

        if (oldDependenciesSnapshot == null) {
            File dependenciesListFile = FasterCUtils.getCachedDependListFile(fasterCVariant.project,fasterCVariant.variantName)
            oldDependenciesSnapshot = StringSnapshot.load(dependenciesListFile,StringSnapshot.class)
        }
        return !dependenciesSnapshot.diff(oldDependenciesSnapshot).getAllChangedDiffInfos().isEmpty()
    }

    /**
     * 保存全量打包时的依赖列表
     */
    def saveDependenciesSnapshot() {
        if (dependenciesSnapshot == null) {
            dependenciesSnapshot = new StringSnapshot(GradleUtils.getCurrentDependList(fasterCVariant.project,fasterCVariant.androidVariant))
        }
        saveDependenciesSnapshot(dependenciesSnapshot)
    }

    /**
     * 保存依赖列表
     * @param snapshot
     * @return
     */
    def saveDependenciesSnapshot(StringSnapshot snapshot) {
        File dependenciesListFile = FasterCUtils.getCachedDependListFile(fasterCVariant.project,fasterCVariant.variantName)

        StringSnapshot stringSnapshot = new StringSnapshot()
        stringSnapshot.nodes = snapshot.nodes
        stringSnapshot.serializeTo(new FileOutputStream(dependenciesListFile))
    }

    def onDexGenerateSuccess(boolean normalBuild,boolean dexMerge) {
        if (normalBuild) {
            //save sourceSet
            saveCurrentSourceSetSnapshot()
            //save dependencies
            saveDependenciesSnapshot()
        }
        else {
            if (dexMerge) {
                //save snapshot and diffinfo
                saveCurrentSourceSetSnapshot()
                deleteLastDiffResultSet()
            }
        }
    }


}
