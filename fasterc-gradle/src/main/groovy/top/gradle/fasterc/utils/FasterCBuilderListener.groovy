package top.gradle.fasterc.utils

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState
import org.gradle.util.Clock
import top.gradle.fasterc.FasterCPlugin
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils

import java.lang.management.ManagementFactory

/**
 * 监听，用于监听编译阶段各阶段的耗时情况
 */

class FasterCBuilderListener implements TaskExecutionListener, BuildListener {


    private Clock clock
    private times = []
    private Project project

    FasterCBuilderListener(Project project) {
        this.project = project
    }

    //==================TaskExecutionListener====================

    @Override
    void beforeExecute(Task task) {
        clock = new Clock();
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {
        def ms = clock.timeInMs
        times.add([ms, task.path])
    }

    //==================TaskExecutionListener====================

    //==================BuildListener====================
    @Override
    void buildStarted(Gradle gradle) {}

    @Override
    void settingsEvaluated(Settings settings) {}

    @Override
    void projectsLoaded(Gradle gradle) {}

    @Override
    void projectsEvaluated(Gradle gradle) {}

    @Override
    void buildFinished(BuildResult buildResult) {
        if (buildResult.failure == null) {
            println "=======任务各阶段耗时情况开始======="
            for (time in times) {
                if (time[0] >= 50) {
                    project.logger.error("耗时:" + time[0] + "ms   任务名称 " + time[1])
                }
            }
            println "=======任务各阶段耗时情况结束======="
        } else {//出错的情况
            //不是com.android.application应用直接返回
            if (project == null || !project.plugins.hasPlugin("com.android.application")) {
                return
            }
            Throwable cause = getRootThowable(buildResult.failure)
            if (cause == null) {
                return
            }
            if (cause instanceof FasterCRuntimeException) {
                return
            }
            StackTraceElement[] stackTrace = cause.getStackTrace()
            if (stackTrace == null || stackTrace.length == 0) {
                return
            }
            StackTraceElement stackTraceElement = stackTrace[0]
            if (stackTraceElement == null) {
                return
            }


            if (stackTraceElement.toString().contains(FasterCPlugin.class.getPackage().getName())) {
                File errorLogFile = new File(FasterCUtils.getBuildDir(project),ShareConstants.ERROR_REPORT_FILENAME)

                Map<String,String> map = getStudioInfo()

                println("\n===========================fasterc error report===========================")
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
                buildResult.failure.printStackTrace(new PrintStream(bos))

                String splitStr = "\n\n"
                StringBuilder report = new StringBuilder()
                //让android studio的Messages窗口显示打开Gradle Console的提示
                report.append("Caused by: ----------------------------------fasterc---------------------------------\n")
                report.append("Caused by: Open the Gradle Console in the lower right corner to view the build error report\n")
                report.append("Caused by: ${errorLogFile}\n")
                report.append("Caused by: ----------------------------------fasterc---------------------------------${splitStr}")
                report.append("${new String(bos.toByteArray())}\n")

                String str =  "fasterc build version     "
//                report.append("fasterc build version     : ${Version.fasterc_BUILD_VERSION}\n")
                report.append("OS                        : ${getOsName()}\n")
                report.append("android_build_version     : ${GradleUtils.GRADLE_VERSION}\n")
                report.append("gradle_version            : ${project.gradle.gradleVersion}\n")
                report.append("buildToolsVersion         : ${project.android.buildToolsVersion.toString()}\n")
                report.append("compileSdkVersion         : ${project.android.compileSdkVersion.toString()}\n")
                report.append("default minSdkVersion     : ${project.android.defaultConfig.minSdkVersion.getApiString()}\n")
                report.append("default targetSdkVersion  : ${project.android.defaultConfig.targetSdkVersion.getApiString()}\n")
                report.append("default multiDexEnabled   : ${project.android.defaultConfig.multiDexEnabled}\n\n")

                try {
                    int keyLength = str.length();
                    if (!map.isEmpty()) {
                        for (String key : map.keySet()) {
                            int dsize = keyLength - key.length();
                            report.append(key + getSpaceString(dsize) + ": " + map.get(key) + "\n");
                        }

                        if (!Boolean.parseBoolean(map.get("instant_run_disabled"))) {
                            report.append("fasterc does not support instant run mode, please disable instant run in 'File->Settings...'.\n\n")
                        }
                        else {
                            report.append("\n")
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace()
                }

                report.append("fasterc build exception")
                System.err.println(report.toString())
                System.err.println("${errorLogFile}")

                int idx = report.indexOf(splitStr)
                String content = report.toString()
                if (idx != -1 && (idx + splitStr.length()) < content.length()) {
                    content = content.substring(idx + splitStr.length())
                }
                FileUtils.writeString2File(content,errorLogFile)
                println("\n===========================fasterc error report===========================")
            }

        }
    }

    //==================BuildListener====================


    static String getOsName() {
        try {
            return System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
        } catch (Throwable e) {
            e.printStackTrace();
            return ""
        }
    }

    Throwable getRootThowable(Throwable throwable) {
        return throwable.cause != null ? getRootThowable(throwable.cause) : throwable
    }

    public Map<String,String> getStudioInfo() {
        Map<String,String> map = new HashMap<>()
        if (Os.isFamily(Os.FAMILY_MAC)) {
            try {
                File script = new File(FasterCUtils.getBuildDir(project),ShareConstants.STUDIO_INFO_SCRIPT_MACOS)
                if (!FileUtils.isLegalFile(script)) {
                    FileUtils.copyResourceUsingStream(ShareConstants.STUDIO_INFO_SCRIPT_MACOS,script)
                }

                int pid = getPid();
                if (pid == -1) {
                    return map;
                }

                Process process = new ProcessBuilder("sh",script.getAbsolutePath(),"${pid}").start();
                int status = process.waitFor();
                if (status == 0) {
                    byte[] bytes = FileUtils.readStream(process.getInputStream());
                    String response = new String(bytes);
                    BufferedReader reader = new BufferedReader(new StringReader(response));
                    System.out.println();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        String[] arr = line.split("=");
                        if (arr != null && arr.length == 2) {
                            map.put(arr[0],arr[1]);
                        }
                    }
                }
                process.destroy();
            } catch (Throwable e) {
                e.printStackTrace()
            }
        }
        return map
    }

    public static String getSpaceString(int count) {
        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(" ");
            }
            return sb.toString();
        }
        return "";
    }

    public static int getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (name != null) {
            String[] arr = name.split("@");
            try {
                return Integer.valueOf(arr[0]);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    /**
     * 给各阶段任务添加监控
     * @param project
     */
    public static void addTaskListener(Project project) {
        FasterCBuilderListener listener = new FasterCBuilderListener(project)
        project.gradle.addListener(listener)
    }

}

