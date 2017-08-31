package top.gradle.fasterc.utils

import org.gradle.api.Project
import org.gradle.api.Task;

/**
 * Created by yanjie on 2017-08-18.
 * Describe: 任务工厂，主要提供各种任务
 */

public class FasterCTaskFactory {
    /**
     * 获得tinker的任务，因为tinker也会对任务Manifest文件进行修改
     */
    public static Task getTinkerPatchManifestTask(Project project, String variantName) {
        String tinkerPatchManifestTaskName = "tinkerpatchSupportProcess${variantName}Manifest"
        try {
            return project.tasks.getByName(tinkerPatchManifestTaskName)
        } catch (Throwable e) {
            return null
        }
    }

    /**
     * 获取系统的合并资源任务
     */
    public static Task getMergeDebugResources(Project project, String variantName) {
        String mergeResourcesTaskName = "merge${variantName}Resources"
        project.tasks.getByName(mergeResourcesTaskName)
    }

    /**
     * 获取分析类在那个dex的任务
     */
    public static Task getTransformClassesWithMultidexlistTask(Project project, String variantName) {
        String transformClassesWithMultidexlistTaskName = "transformClassesWithMultidexlistFor${variantName}"
        try {
            return project.tasks.getByName(transformClassesWithMultidexlistTaskName)
        } catch (Throwable e) {
            return null
        }
    }

    /**
     * 获得谷歌插件的class转换为dex的任务
     */
    public static Task getTransformClassesWithDex(Project project, String variantName) {
        String taskName = "transformClassesWithDexFor${variantName}"
        return project.tasks.getByName(taskName)
    }

    public static Task getCollectMultiDexComponentsTask(Project project, String variantName) {
        try {
            String collectMultiDexComponents = "collect${variantName}MultiDexComponents"
            return project.tasks.findByName(collectMultiDexComponents)
        } catch (Throwable e) {
            return null
        }
    }
}
