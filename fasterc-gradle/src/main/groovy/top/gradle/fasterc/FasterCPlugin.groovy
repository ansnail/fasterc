package top.gradle.fasterc

import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.build.gradle.internal.transforms.JarMergingTransform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import top.gradle.fasterc.config.FasterCConfig
import top.gradle.fasterc.tasks.*
import top.gradle.fasterc.transform.FasterCJarMergingTransform
import top.gradle.fasterc.transform.FasterCTransform
import top.gradle.fasterc.utils.*
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.constants.ShareConstants

import java.lang.reflect.Field

/**
 * Created by yanjie on 2017-07-31.
 * Describe:自定义插件入口类
 */

class FasterCPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        //在工程中加入自定义配置选项，用于控制各种配置信息
        project.extensions.create('fasterc', FasterCConfig)
        //给各阶段任务添加耗时监听
        FasterCBuilderListener.addTaskListener(project)
        //系统任务有向图建立后的回调
        project.afterEvaluate {
            //获取系统配置中的android对象
            def android = project.extensions.android
            //dex的参数优化
            android.dexOptions.jumboMode = true
            try {
                android.dexOptions.preDexLibraries = false
            } catch (Throwable e) {
                //no preDexLibraries field, just continue
                e.printStackTrace()
            }

            //获得全局配置选项，后面的选项都从这个配置里面取
            def fasterCConfig = project.fasterc
            //fasterc是否禁用
            if (!fasterCConfig.fasterCEnable) {
                FasterCLog.error(project, "fasterc plugins are disabled.")
                return
            }
            //必须有com.android.application插件
            if (!project.plugins.hasPlugin('com.android.application')) {
                throw new FasterCRuntimeException('Android Application plugin required')
            }
            //检查最低支持的gradle版本
            if (GradleUtils.GRADLE_VERSION < ShareConstants.MIN_SUPPORT_ANDROID_GRADLE_VERSION) {
                throw new FasterCRuntimeException("minimum support gradle version is ${ShareConstants.MIN_SUPPORT_ANDROID_GRADLE_VERSION}")
            }
            //每一种模式的处理，主要的debug和release版本
            android.applicationVariants.each { variant ->
                //输出类型
                def variantOutput = variant.outputs.first()
                //版本模式名称
                def variantName = variant.name.capitalize()
                //与instant run有冲突需要禁掉instant run
                try {
                    def instantRunTask = project.tasks.getByName("transformClassesWithInstantRunFor${variantName}")
                    if (instantRunTask){
                        throw new FasterCRuntimeException("Please disable instant run in 'File->Settings...'.")
                    }
                } catch (UnknownTaskException e) {
                    // Not in instant run mode, continue.
                }
                //生成全局数据存储实例
                FasterCLog.error(project,"FasterCVariant start generate")
                FasterCVariant fasterCVariant = new FasterCVariant(project, variant)
                FasterCLog.error(project,fasterCVariant.toString())

                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled
                if (proguardEnable) {
                    throw new FasterCRuntimeException("fasterc don't support minifyEnabled=true")
                } else {

                    /**
                     =======================自定义任务执行顺序=======================
                     。。。==>processManifest==>FasterCManifestTask==>FasterCResourceIdTask==>
                     processResources==>FasterCPrepareTask==>FasterCCustomJavacTask==>
                     javaCompile==>。。。
                     =======================自定义任务执行顺序=======================
                     */

                    //解决tinker也替换Application的问题,把替换逻辑放在后面
                    def tinkerPatchManifestTask = FasterCTaskFactory.getTinkerPatchManifestTask(project, variantName)
                    if (tinkerPatchManifestTask != null) {
                        manifestTask.mustRunAfter tinkerPatchManifestTask
                    }

                    //替换项目的Application为top.dex.fasterc.FasterCApplication
                    FasterCManifestTask manifestTask = project.tasks.create("fastercProcess${variantName}Manifest", FasterCManifestTask)
                    variantOutput.processManifest.dependsOn FasterCTaskFactory.getMergeDebugResources(project, variantName)
                    manifestTask.fasterCVariant = fasterCVariant
                    manifestTask.mustRunAfter variantOutput.processManifest
                    variantOutput.processResources.dependsOn manifestTask

                    //保持补丁打包时R文件中相同的节点和第一次打包时的值保持一致
                    FasterCResourceIdTask resourceIdTask = project.tasks.create("fastercProcess${variantName}ResourceId", FasterCResourceIdTask)
                    resourceIdTask.fasterCVariant = fasterCVariant
                    resourceIdTask.resDir = variantOutput.processResources.resDir
                    resourceIdTask.mustRunAfter manifestTask
                    variantOutput.processResources.dependsOn resourceIdTask

                    //准备上下文环境
                    FasterCPrepareTask prepareTask = project.tasks.create("fastercPrepareFor${variantName}", FasterCPrepareTask)
                    prepareTask.fasterCVariant = fasterCVariant
                    prepareTask.mustRunAfter variantOutput.processResources

                    //是否使用自定义编译任务
                    if (fasterCConfig.useCustomCompile) {
                        FasterCCustomJavacTask customJavacTask = project.tasks.create("fastercCustomCompile${variantName}JavaWithJavac", FasterCCustomJavacTask)
                        customJavacTask.fasterCVariant = fasterCVariant
                        customJavacTask.dependsOn prepareTask
                        variant.javaCompile.dependsOn customJavacTask
                    } else {
                        variant.javaCompile.dependsOn prepareTask
                    }

                    //transformClassesWithMultidexlistFor${variantName}的作用是计算哪些类必须放在第一个dex里面，由于fasterc使用替换Application的方案隔离了项目代码的dex，
                    //所以这个任务就没有存在的意义了，禁止掉这个任务以提高打包速度，但是transformClassesWithDexFor${variantName}会使用这个任务输出的txt文件，
                    //所以就生成一个空文件防止报错
                    Task multidexlistTask = FasterCTaskFactory.getTransformClassesWithMultidexlistTask(project, variantName)
                    if (multidexlistTask != null) {
                        FasterCCreateMaindexlistFileTask createFileTask = project.tasks.create("fastercCreate${variantName}MaindexlistFileTask", FasterCCreateMaindexlistFileTask)
                        createFileTask.fasterCVariant = fasterCVariant
                        multidexlistTask.dependsOn createFileTask
                        multidexlistTask.enabled = false
                    }
                    def collectMultiDexComponentsTask = FasterCTaskFactory.getCollectMultiDexComponentsTask(project, variantName)
                    if (collectMultiDexComponentsTask != null) {
                        collectMultiDexComponentsTask.enabled = false
                    }

                    //检测设备的连接情况
                    FasterCInstallApkTask fasterCInstallApkTask = project.tasks.create("fasterc${variantName}", FasterCInstallApkTask)
                    fasterCInstallApkTask.fasterCVariant = fasterCVariant
                    fasterCInstallApkTask.dependsOn variant.assemble
                    fasterCVariant.fasterCInstallApkTask = fasterCInstallApkTask
                    //当生成dex后保存一些编译信息
                    FasterCTaskFactory.getTransformClassesWithDex(project, variantName).doLast {
                        fasterCInstallApkTask.onDexTransformComplete()
                    }

                    //给插件各任务流程添加监听
                    project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
                        @Override
                        public void graphPopulated(TaskExecutionGraph taskGraph) {
                            for (Task task : taskGraph.getAllTasks()) {
                                if (task.getProject() == project && task instanceof TransformTask && task.name.toLowerCase().contains(variant.name.toLowerCase())) {
                                    Transform transform = ((TransformTask) task).getTransform()
                                    //如果开启了multiDexEnabled true,存在transformClassesWithJarMergingFor${variantName}任务
                                    if ((((transform instanceof JarMergingTransform)) && !(transform instanceof FasterCJarMergingTransform))) {
                                        fasterCVariant.hasJarMergingTask = true
                                        if (fasterCConfig.debug) {
                                            FasterCLog.error(project,"find jarmerging transform. transform class: " + task.transform.getClass() + " . task name: " + task.name)
                                        }

                                        FasterCJarMergingTransform jarMergingTransform = new FasterCJarMergingTransform(transform, fasterCVariant)
                                        Field field = TransformTask.class.getDeclaredField("transform")
                                        field.setAccessible(true)
                                        field.set(task, jarMergingTransform)
                                    }

                                    if ((((transform instanceof DexTransform)) && !(transform instanceof FasterCTransform))) {
                                        if (fasterCConfig.debug) {
                                            FasterCLog.error(project,"find dex transform. transform class: " + task.transform.getClass() + " . task name: " + task.name)
                                        }
                                        //代理DexTransform,实现自定义的转换
                                        FasterCTransform fasterCTransform = new FasterCTransform(transform, fasterCVariant)
                                        Field field = TransformTask.class.getDeclaredField("transform")
                                        field.setAccessible(true)
                                        field.set(task, fasterCTransform)
                                    }

                                }

                            }
                        }
                    });
                }
            }
        }
    }
}
