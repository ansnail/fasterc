package top.gradle.fasterc.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.utils.FileUtils

/**
 * Created by yanjie on 2017-08-18.
 * Describe:
 * transformClassesWithDexFor${variantName}会使用这个任务输出的txt文件，所以需要生成一个空文件防止报错
 */

public class FasterCCreateMaindexlistFileTask extends DefaultTask {
    FasterCVariant fasterCVariant

    FasterCCreateMaindexlistFileTask() {
        group = 'fasterc'
    }

    @TaskAction
    void createFile() {
        FasterCLog.error(project,"FasterCCreateMaindexlistFileTask has execute........")
        if (fasterCVariant.androidVariant != null) {
            File maindexlistFile = fasterCVariant.androidVariant.getVariantData().getScope().getMainDexListFile()
            File parentFile = maindexlistFile.getParentFile()
            FileUtils.ensureDir(parentFile)

            if (!FileUtils.isLegalFile(maindexlistFile)) {
                maindexlistFile.createNewFile()
            }
        }
    }
}
