package top.gradle.fasterc.transform

import com.android.build.api.transform.*
import com.google.common.collect.Lists
import org.gradle.api.Project
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.utils.FasterCRuntimeException
import top.gradle.fasterc.utils.FasterCUtils
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils
import top.gradle.fasterc.inject.ClassInject
import top.gradle.fasterc.utils.GradleUtils
import top.gradle.fasterc.utils.DexOperationUtils

/**
 * 用于dex生成
 * 全量打包时的流程:
 * 1、合并所有的class文件生成一个jar包
 * 2、扫描所有的项目代码并且在构造方法里添加对top.dex.fasterc.antilazyload.AntiLazyLoad类的依赖
 *    这样做的目的是为了解决class verify的问题，
 *    详情请看https://mp.weixin.qq.com/s?__biz=MzI1MTA1MzM2Nw==&mid=400118620&idx=1&sn=b4fdd5055731290eef12ad0d17f39d4a
 * 3、对项目代码做快照，为了以后补丁打包时对比那些java文件发生了变化
 * 4、对当前项目的所以依赖做快照，为了以后补丁打包时对比依赖是否发生了变化，如果变化需要清除缓存
 * 5、调用真正的transform生成dex
 * 6、缓存生成的dex，并且把fasterc-runtime.dex插入到dex列表中，假如生成了两个dex，classes.dex classes2.dex 需要做一下操作
 *    fasterc-runtime.dex => classes.dex
 *    classes.dex         => classes2.dex
 *    classes2.dex        => classes3.dex
 *    然后运行期在入口Application(top.dex.fasterc.FasterCApplication)使用MultiDex把所有的dex加载进来
 * 7、保存资源映射映射表，为了保持id的值一致，详情看
 * @see top.gradle.fasterc.tasks.FasterCResourceIdTask
 *
 * 补丁打包时的流程
 * 1、检查缓存的有效性
 * @see top.gradle.fasterc.tasks.FasterCCustomJavacTask 的prepareEnv方法说明
 * 2、扫描所有变化的java文件并编译成class
 * @see top.gradle.fasterc.tasks.FasterCCustomJavacTask
 * 3、合并所有变化的class并生成jar包
 * 4、生成补丁dex
 * 5、把所有的dex按照一定规律放在transformClassesWithMultidexlistFor${variantName}任务的输出目录
 *    fasterc-runtime.dex    => classes.dex
 *    patch.dex              => classes2.dex
 *    dex_cache.classes.dex  => classes3.dex
 *    dex_cache.classes2.dex => classes4.dex
 *    dex_cache.classesN.dex => classes(N + 2).dex
 *
 */
class FasterCTransform extends TransformProxy {
    FasterCVariant fasterCVariant

    Project project
    String variantName

    FasterCTransform(Transform base, FasterCVariant fasterCVariant) {
        super(base)
        this.fasterCVariant = fasterCVariant
        this.project = fasterCVariant.project
        this.variantName = fasterCVariant.variantName
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
        if (fasterCVariant.hasDexCache) {
            FasterCLog.error(project,"patch transform start,we will generate dex file")
            if (fasterCVariant.projectSnapshot.diffResultSet.isJavaFileChanged()) {
                //生成补丁jar包
                File patchJar = generatePatchJar(transformInvocation)
                File patchDex = FasterCUtils.getPatchDexFile(fasterCVariant.project,fasterCVariant.variantName)
                DexOperationUtils.generatePatchDex(fasterCVariant,base,patchJar,patchDex)
                //获取dex输出路径
                File dexOutputDir = GradleUtils.getDexOutputDir(base,transformInvocation)
                //merged dex
                File mergedPatchDexDir = FasterCUtils.getMergedPatchDexDir(fasterCVariant.project,fasterCVariant.variantName)

                if (fasterCVariant.willExecDexMerge()) {
                    //merge dex
                    if (FileUtils.hasDex(mergedPatchDexDir)) {
                        //已经执行过一次dex merge
                        File cacheDexDir = FasterCUtils.getDexCacheDir(project,variantName)
                        //File outputDex = new File(dexOutputDir,"merged-patch.dex")
                        File mergedPatchDex = new File(mergedPatchDexDir,ShareConstants.CLASSES_DEX)
                        //更新patch.dex
                        DexOperationUtils.mergeDex(fasterCVariant,mergedPatchDex,patchDex,mergedPatchDex)
                        FileUtils.cleanDir(dexOutputDir)

                        FileUtils.copyDir(cacheDexDir,dexOutputDir,ShareConstants.DEX_SUFFIX)

                        incrementDexDir(dexOutputDir,2)
                        //copy merged-patch.dex
                        FileUtils.copyFile(mergedPatchDex,new File(dexOutputDir,"${ShareConstants.CLASSES}2${ShareConstants.DEX_SUFFIX}"))
                        //copy fasterc-runtime.dex
                        FileUtils.copyResourceUsingStream(ShareConstants.RUNTIME_DEX_FILENAME,new File(dexOutputDir,ShareConstants.CLASSES_DEX))
                    }else {
                        //第一只执行dex merge,直接保存patchDex
                        //patch.dex              => classes.dex
                        //dex_cache.classes.dex  => classes2.dex
                        //dex_cache.classes2.dex => classes3.dex
                        //dex_cache.classesN.dex => classes(N + 1).dex
                        //复制补丁dex到输出路径
                        hookPatchBuildDex(dexOutputDir,mergedPatchDexDir,patchDex)

                        FileUtils.cleanDir(mergedPatchDexDir)
                        FileUtils.ensureDir(mergedPatchDexDir)
                        patchDex.renameTo(new File(mergedPatchDexDir,ShareConstants.CLASSES_DEX))
                    }
                    fasterCVariant.onDexGenerateSuccess(false,true)
                }else {
                    fasterCVariant.metaInfo.patchDexVersion += 1
                    //复制补丁打包的dex到输出路径
                    hookPatchBuildDex(dexOutputDir,mergedPatchDexDir,patchDex)
                    fasterCVariant.onDexGenerateSuccess(false,false)
                }
            }else {
                FasterCLog.error(project,"no java files have changed, just ignore")
            }
        }else {
            def config = fasterCVariant.androidVariant.getVariantData().getVariantConfiguration()
            boolean isMultiDexEnabled = config.isMultiDexEnabled()

            FasterCLog.error(project,"normal transform start")
            if (isMultiDexEnabled) {
                if (fasterCVariant.executedJarMerge) {
                    //如果开启了multidex,FasterCJarMergingTransform完成了inject的操作，不需要在做处理
                    File combinedJar = getCombinedJarFile(transformInvocation)

                    if (fasterCVariant.configuration.useCustomCompile) {
                        File injectedJar = FasterCUtils.getInjectedJarFile(project,variantName)
                        FileUtils.copyFile(combinedJar,injectedJar)
                    }
                } else {
                    ClassInject.injectTransformInvocation(fasterCVariant,transformInvocation)
                    File injectedJar = FasterCUtils.getInjectedJarFile(project,variantName)
                    GradleUtils.executeMerge(project,transformInvocation,injectedJar)
                    transformInvocation = GradleUtils.createNewTransformInvocation(base,transformInvocation,injectedJar)
                }
            }else {
                //如果没有开启multidex需要在此处做注入
                ClassInject.injectTransformInvocation(fasterCVariant,transformInvocation)
                if (fasterCVariant.configuration.useCustomCompile) {
                    File injectedJar = FasterCUtils.getInjectedJarFile(project,variantName)
                    GradleUtils.executeMerge(project,transformInvocation,injectedJar)
                }
            }
            FasterCLog.error(fasterCVariant.project,"===dex===ok===")
            //调用默认转换方法
            base.transform(transformInvocation)
            //获取dex输出路径
            File dexOutputDir = GradleUtils.getDexOutputDir(base,transformInvocation)
            //缓存dex
            int dexCount = cacheNormalBuildDex(dexOutputDir)
            //复制全量打包的dex到输出路径
            hookNormalBuildDex(dexOutputDir)

            fasterCVariant.metaInfo.dexCount = dexCount
            fasterCVariant.metaInfo.buildMillis = System.currentTimeMillis()

            fasterCVariant.onDexGenerateSuccess(true,false)
            FasterCLog.error(project,"normal transform end")
        }

        fasterCVariant.executedDexTransform = true
    }

    /**
     * 获取输出jar路径
     * @param invocation
     * @return
     */
    public static File getCombinedJarFile(TransformInvocation invocation) {
        List<JarInput> jarInputs = Lists.newArrayList();
        for (TransformInput input : invocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
        }
        if (jarInputs.size() != 1) {
            throw new FasterCRuntimeException("jar input size is ${jarInputs.size()}, expected is 1")
        }
        File combinedJar = jarInputs.get(0).getFile()
        return combinedJar
    }

    /**
     * 生成补丁jar包
     * @param transformInvocation
     * @return
     */
    File generatePatchJar(TransformInvocation transformInvocation) {
        def config = fasterCVariant.androidVariant.getVariantData().getVariantConfiguration()
        boolean isMultiDexEnabled = config.isMultiDexEnabled()
        if (isMultiDexEnabled && (fasterCVariant.executedJarMerge || fasterCVariant.hasJarMergingTask)) {
            //如果开启了multidex,FasterCJarMergingTransform完成了jar merge的操作
            File patchJar = getCombinedJarFile(transformInvocation)
            FasterCLog.error(project,"multiDex enabled use patch.jar: ${patchJar}")
            return patchJar
        }
        else {
            //补丁jar
            File patchJar = new File(FasterCUtils.getBuildDir(project,variantName),"patch-combined.jar")
            //生成补丁jar
            JarOperation.generatePatchJar(fasterCVariant,transformInvocation,patchJar)
            return patchJar
        }
    }

    /**
     * 缓存全量打包时生成的dex
     * @param dexOutputDir dex输出路径
     */
    int cacheNormalBuildDex(File dexOutputDir) {
        FasterCLog.error(project,"dex output directory: " + dexOutputDir)

        int dexCount = 0
        File cacheDexDir = FasterCUtils.getDexCacheDir(project,variantName)
        File[] files = dexOutputDir.listFiles()
        files.each { file ->
            if (file.getName().endsWith(ShareConstants.DEX_SUFFIX)) {
                FileUtils.copyFile(file,new File(cacheDexDir,file.getName()))
                dexCount = dexCount + 1
            }
        }
        return dexCount
    }

    static void incrementDexDir(File dexDir) {
        incrementDexDir(dexDir,1)
    }

    /**
     * 递增指定目录中的dex
     *
     * classes.dex   => classes2.dex
     * classes2.dex  => classes3.dex
     * classesN.dex  => classes(N + 1).dex
     *
     * @param dexDir
     */
    static void incrementDexDir(File dexDir, int dsize) {
        if (dsize <= 0) {
            throw new RuntimeException("dsize must be greater than 0!")
        }
        //classes.dex  => classes2.dex.tmp
        //classes2.dex => classes3.dex.tmp
        //classesN.dex => classes(N + 1).dex.tmp

        String tmpSuffix = ".tmp"
        File classesDex = new File(dexDir,ShareConstants.CLASSES_DEX)
        if (FileUtils.isLegalFile(classesDex)) {
            classesDex.renameTo(new File(dexDir,"classes${dsize + 1}.dex${tmpSuffix}"))
        }
        int point = 2
        File dexFile = new File(dexDir,"${ShareConstants.CLASSES}${point}${ShareConstants.DEX_SUFFIX}")
        while (FileUtils.isLegalFile(dexFile)) {
            new File(dexDir,"classes${point}.dex").renameTo(new File(dexDir,"classes${point + dsize}.dex${tmpSuffix}"))
            point++
            dexFile = new File(dexDir,"classes${point}.dex")
        }

        //classes2.dex.tmp => classes2.dex
        //classes3.dex.tmp => classes3.dex
        //classesN.dex.tmp => classesN.dex
        point = dsize + 1
        dexFile = new File(dexDir,"classes${point}.dex${tmpSuffix}")
        while (FileUtils.isLegalFile(dexFile)) {
            dexFile.renameTo(new File(dexDir,"classes${point}.dex"))
            point++
            dexFile = new File(dexDir,"classes${point}.dex${tmpSuffix}")
        }
    }

    /**
     * 全量打包时复制dex到指定位置
     * @param dexOutputDir dex输出路径
     */
    void hookNormalBuildDex(File dexOutputDir) {
        //dexelements [fasterc-runtime.dex ${dex_cache}.listFiles]
        //runtime.dex            => classes.dex
        //dex_cache.classes.dex  => classes2.dex
        //dex_cache.classes2.dex => classes3.dex
        //dex_cache.classesN.dex => classes(N + 1).dex

        incrementDexDir(dexOutputDir)

        //fasterc-runtime.dex = > classes.dex
        FileUtils.copyResourceUsingStream(ShareConstants.RUNTIME_DEX_FILENAME,new File(dexOutputDir,ShareConstants.CLASSES_DEX))
        printLogWhenDexGenerateComplete(dexOutputDir,true)
    }

    /**
     * 补丁打包时复制dex到指定位置
     * @param dexOutputDir dex输出路径
     */
    void hookPatchBuildDex(File dexOutputDir,File mergedPatchDexDir,File patchDex) {
        //dexelements [fasterc-runtime.dex patch.dex ${dex_cache}.listFiles]
        //runtime.dex            => classes.dex
        //patch.dex              => classes2.dex
        //dex_cache.classes.dex  => classes3.dex
        //dex_cache.classes2.dex => classes4.dex
        //dex_cache.classesN.dex => classes(N + 2).dex
        FasterCLog.error(project,"patch transform hook patch dex start")

        FileUtils.cleanDir(dexOutputDir)
        File mergedPatchDex = new File(mergedPatchDexDir,ShareConstants.CLASSES_DEX)
        File cacheDexDir = FasterCUtils.getDexCacheDir(project,variantName)

        //copy fasterc-runtime.dex
        FileUtils.copyResourceUsingStream(ShareConstants.RUNTIME_DEX_FILENAME,new File(dexOutputDir,ShareConstants.CLASSES_DEX))
        //copy patch.dex
        FileUtils.copyFile(patchDex,new File(dexOutputDir,"classes2.dex"))
        if (FileUtils.fileExists(mergedPatchDex.absolutePath)) {
            FileUtils.copyFile(mergedPatchDex,new File(dexOutputDir,"classes3.dex"))
            FileUtils.copyFile(new File(cacheDexDir,ShareConstants.CLASSES_DEX),new File(dexOutputDir,"classes4.dex"))

            int point = 2
            File dexFile = new File(cacheDexDir,"${ShareConstants.CLASSES}${point}${ShareConstants.DEX_SUFFIX}")
            while (FileUtils.isLegalFile(dexFile)) {
                FileUtils.copyFile(dexFile,new File(dexOutputDir,"${ShareConstants.CLASSES}${point + 3}${ShareConstants.DEX_SUFFIX}"))
                point++
                dexFile = new File(cacheDexDir,"${ShareConstants.CLASSES}${point}${ShareConstants.DEX_SUFFIX}")
            }
        }
        else {
            FileUtils.copyFile(new File(cacheDexDir,ShareConstants.CLASSES_DEX),new File(dexOutputDir,"classes3.dex"))
            int point = 2
            File dexFile = new File(cacheDexDir,"${ShareConstants.CLASSES}${point}${ShareConstants.DEX_SUFFIX}")
            while (FileUtils.isLegalFile(dexFile)) {
                FileUtils.copyFile(dexFile,new File(dexOutputDir,"${ShareConstants.CLASSES}${point + 2}${ShareConstants.DEX_SUFFIX}"))
                point++
                dexFile = new File(cacheDexDir,"${ShareConstants.CLASSES}${point}${ShareConstants.DEX_SUFFIX}")
            }
        }
        printLogWhenDexGenerateComplete(dexOutputDir,false)
    }

    /**
     * 当dex生成完成后打印日志
     * @param normalBuild
     */
    void printLogWhenDexGenerateComplete(File dexOutputDir,boolean normalBuild) {
        File cacheDexDir = FasterCUtils.getDexCacheDir(project,variantName)

        //log
        StringBuilder sb = new StringBuilder()
        sb.append("cached_dex[")
        File[] dexFiles = cacheDexDir.listFiles()
        for (File file : dexFiles) {
            if (file.getName().endsWith(ShareConstants.DEX_SUFFIX)) {
                sb.append(file.getName())
                if (file != dexFiles[dexFiles.length - 1]) {
                    sb.append(",")
                }
            }
        }
        sb.append("] cur-dex[")
        dexFiles = dexOutputDir.listFiles()
        int idx = 0
        for (File file : dexFiles) {
            if (file.getName().endsWith(ShareConstants.DEX_SUFFIX)) {
                sb.append(file.getName())
                if (idx < (dexFiles.length - 1)) {
                    sb.append(",")
                }
            }
            idx ++
        }
        sb.append("]")
        if (normalBuild) {
            FasterCLog.error(project,"first build ${sb}")
        }
        else {
            FasterCLog.error(project,"patch build ${sb}")
        }
    }
}