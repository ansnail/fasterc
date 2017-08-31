package top.gradle.fasterc.variant

import org.gradle.api.Project
import top.gradle.fasterc.config.FasterCConfig
import top.gradle.fasterc.tasks.FasterCInstallApkTask
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.utils.FasterCRuntimeException
import top.gradle.fasterc.utils.FasterCUtils
import top.gradle.fasterc.utils.GradleUtils
import top.lib.fasterc.utils.FileUtils
import top.lib.fasterc.utils.SerializeUtils

/**
 * Created by yanjie on 2017-08-16.
 * Describe: 主要用于环境准备和全局参数的存储
 */

public class FasterCVariant {

    final Project project
    final FasterCConfig configuration   //配置选项
    final def androidVariant    //android全局数据实例
    final String variantName    //编译类型
    final String manifestPath   //manifest文件位置
    final File rootBuildDir     //构建根目录
    final File buildDir         //构建目录(rootBuildDir/debug)
    final ProjectSnapshot projectSnapshot   //工程快照
    final Set<LibDependency> libraryDependencies //项目的依赖
    String originPackageName    //原始包名
    String mergedPackageName    //修改替换后的包名
    boolean hasDexCache         //是否有dex
    boolean firstPatchBuild     //是否是第一次构建
    boolean initialized         //是否初始化，准备过
    boolean hasJarMergingTask
    boolean executedJarMerge
    boolean executedDexTransform
    MetaInfo metaInfo
    FasterCInstallApkTask fasterCInstallApkTask

    FasterCVariant(Project project, Object androidVariant) {
        this.project = project
        this.androidVariant = androidVariant

        this.configuration = project.fasterc
        this.variantName = androidVariant.name.capitalize()
        this.manifestPath = androidVariant.outputs.first().processManifest.manifestOutputFile
        this.rootBuildDir = FasterCUtils.getBuildDir(project)
        this.buildDir = FasterCUtils.getBuildDir(project,variantName)

        projectSnapshot = new ProjectSnapshot(this)
        libraryDependencies = LibDependency.resolveProjectDependency(project,androidVariant)

        if (configuration.dexMergeThreshold <= 0) {
            throw new FasterCRuntimeException("dexMergeThreshold Must be greater than 0!!")
        }
    }


    /**
    * 检查缓存是否过期，如果过期就删除
    */
    void prepareEnv() {
        if (initialized) {
            return
        }
        initialized = true
        hasDexCache = FasterCUtils.hasDexCache(project,variantName)
        if (hasDexCache) {
            File diffResultSetFile = FasterCUtils.getDiffResultSetFile(project,variantName)
            if (!FileUtils.isLegalFile(diffResultSetFile)) {
                firstPatchBuild = true
            }

            try {
                metaInfo = MetaInfo.load(project,variantName)
                if (metaInfo == null) {
                    File metaInfoFile = FasterCUtils.getMetaInfoFile(project,variantName)
                    if (FileUtils.isLegalFile(metaInfoFile)) {
                        throw new FasterCRuntimeException("parse json content fail: ${FasterCUtils.getMetaInfoFile(project,variantName)}")
                    }else {
                        throw new FasterCRuntimeException("miss meta info file: ${FasterCUtils.getMetaInfoFile(project,variantName)}")
                    }
                }

                File cachedDependListFile = FasterCUtils.getCachedDependListFile(project,variantName)
                if (!FileUtils.isLegalFile(cachedDependListFile)) {
                    throw new FasterCRuntimeException("miss depend list file: ${cachedDependListFile}")
                }

                File sourceSetSnapshotFile = FasterCUtils.getSourceSetSnapshotFile(project,variantName)
                if (!FileUtils.isLegalFile(sourceSetSnapshotFile)) {
                    throw new FasterCRuntimeException("miss sourceSet snapshoot file: ${sourceSetSnapshotFile}")
                }

                File resourceMappingFile = FasterCUtils.getResourceMappingFile(project,variantName)
                if (!FileUtils.isLegalFile(resourceMappingFile)) {
                    throw new FasterCRuntimeException("miss resource mapping file: ${resourceMappingFile}")
                }

                if (configuration.useCustomCompile) {
                    File injectedJarFile = FasterCUtils.getInjectedJarFile(project,variantName)
                    if (!FileUtils.isLegalFile(injectedJarFile)) {
                        throw new FasterCRuntimeException("miss injected jar file: ${injectedJarFile}")
                    }
                }

                try {
                    projectSnapshot.loadSnapshot()
                } catch (Throwable e) {
                    e.printStackTrace()
                    throw new FasterCRuntimeException(e)
                }

                if (projectSnapshot.isDependenciesChanged()) {
                    throw new FasterCRuntimeException("dependencies changed")
                }
            } catch (FasterCRuntimeException e) {
                hasDexCache = false
                FasterCLog.error(project,"fasterc ${e.getMessage()}")
                FasterCLog.error(project,"fasterc we will remove ${variantName.toLowerCase()} cache")
            }
        }

        if (hasDexCache) {
            FasterCLog.error(project,"fasterc discover dex cache for ${variantName.toLowerCase()}")
        }
        else {
            metaInfo = new MetaInfo()
            metaInfo.projectPath = project.projectDir.absolutePath
            metaInfo.rootProjectPath = project.rootProject.projectDir.absolutePath
            metaInfo.variantName = variantName
            FasterCUtils.cleanCache(project,variantName)
            FileUtils.ensureDir(buildDir)
        }

        projectSnapshot.prepareEnv()
    }

    /**
     * 获取原始manifest文件的package节点的值
     * @return
     */
    public String getOriginPackageName() {
        if (originPackageName != null) {
            return originPackageName
        }
        String path = project.android.sourceSets.main.manifest.srcFile.absolutePath
        originPackageName = GradleUtils.getPackageName(path)
        return originPackageName
    }

    /**
     * 获取合并以后的manifest文件的package节点的值
     * @return
     */
    public String getMergedPackageName() {
        if (mergedPackageName != null) {
            return mergedPackageName
        }
        mergedPackageName = GradleUtils.getPackageName(manifestPath)
        return mergedPackageName
    }

    /**
     * 当dex生成以后
     * @param normalBuild
     */
    public void onDexGenerateSuccess(boolean normalBuild,boolean dexMerge) {
        if (normalBuild) {
            saveMetaInfo()
            copyRTxt()
        }else {
            if (dexMerge) {
                //移除idx.xml public.xml
                File idsXmlFile = FasterCUtils.getIdxXmlFile(project,variantName)
                File publicXmlFile = FasterCUtils.getPublicXmlFile(project,variantName)
                FileUtils.deleteFile(idsXmlFile)
                FileUtils.deleteFile(publicXmlFile)
                copyRTxt()
            }
        }
        copyMetaInfo2Assets()
        projectSnapshot.onDexGenerateSuccess(normalBuild,dexMerge)
    }

    def saveMetaInfo() {
        File metaInfoFile = FasterCUtils.getMetaInfoFile(project,variantName)
        SerializeUtils.serializeTo(new FileOutputStream(metaInfoFile),metaInfo)
    }

    def copyMetaInfo2Assets() {
        File metaInfoFile = FasterCUtils.getMetaInfoFile(project,variantName)
        File assetsPath = androidVariant.getVariantData().getScope().getMergeAssetsOutputDir()
        FileUtils.copyFile(metaInfoFile, new File(assetsPath, metaInfoFile.getName()))
    }

    /**
     * 保存资源映射文件
     */
    def copyRTxt() {
        File rtxtFile = new File(androidVariant.getVariantData().getScope().getSymbolLocation(),"R.txt")
        FasterCLog.error(project,"before==rtxtFile=="+rtxtFile)
        if (!FileUtils.isLegalFile(rtxtFile)) {
            rtxtFile = new File(project.buildDir,"${File.separator}intermediates${File.separator}symbols${File.separator}${androidVariant.dirName}${File.separator}R.txt")
            FasterCLog.error(project,"after==rtxtFile=="+rtxtFile)
        }
        FileUtils.copyFile(rtxtFile,FasterCUtils.getResourceMappingFile(project,variantName))
    }

    /**
     * 补丁打包是否需要执行dex merge
     * @return
     */
    public boolean willExecDexMerge() {
        return hasDexCache && projectSnapshot.diffResultSet.changedJavaFileDiffInfos.size() >= configuration.dexMergeThreshold
    }


    @Override
    public String toString() {
        return "FasterCVariant{" +
                "project=" + project +
                ", configuration=" + configuration +
                ", androidVariant=" + androidVariant +
                ", variantName='" + variantName + '\'' +
                ", manifestPath='" + manifestPath + '\'' +
                ", rootBuildDir=" + rootBuildDir +
                ", buildDir=" + buildDir +
                ", projectSnapshot=" + projectSnapshot +
                ", libraryDependencies=" + libraryDependencies +
                ", originPackageName='" + originPackageName + '\'' +
                ", mergedPackageName='" + mergedPackageName + '\'' +
                ", hasDexCache=" + hasDexCache +
                ", firstPatchBuild=" + firstPatchBuild +
                ", initialized=" + initialized +
                ", hasJarMergingTask=" + hasJarMergingTask +
                ", executedJarMerge=" + executedJarMerge +
                ", executedDexTransform=" + executedDexTransform +
                ", metaInfo=" + metaInfo +
                '}';
    }
}
