package top.gradle.fasterc.utils

import org.gradle.api.Project;

/**
 * Created by yanjie on 2017-08-15.
 * Describe: 日志类，打印相关日志信息
 */

public class FasterCLog {

    public static error(Project project ,String info){
        project.logger.error("FasterC:"+info);
    }
}
