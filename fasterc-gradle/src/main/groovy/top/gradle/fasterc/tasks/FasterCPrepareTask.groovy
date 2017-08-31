package top.gradle.fasterc.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.variant.FasterCVariant;

/**
 * Created by yanjie on 2017-08-18.
 * Describe: 准备上下文环境
 */

public class FasterCPrepareTask extends DefaultTask{

    FasterCVariant fasterCVariant

    FasterCPrepareTask() {
        group = 'fasterc'
    }

    @TaskAction
    void prepareContext() {
        FasterCLog.error(project,"FasterCPrepareTask has execute........")
        fasterCVariant.prepareEnv()
    }
}
