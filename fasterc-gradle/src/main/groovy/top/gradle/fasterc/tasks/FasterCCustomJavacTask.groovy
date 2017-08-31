package top.gradle.fasterc.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.utils.FasterCUtils
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.snapshot.sourceset.PathInfo
import top.lib.fasterc.snapshot.sourceset.SourceSetDiffResultSet
import top.lib.fasterc.utils.FileUtils

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Created by yanjie on 2017-08-18.
 * Describe:
 *
 * 每次SourceSet下的某个java文件变化时，默认的compile${variantName}JavaWithJavac任务会扫描所有的java文件
 * 处理javax.annotation.processing.AbstractProcessor接口用来代码动态代码生成，所以项目中的java文件如果很多会造成大量的时间浪费
 *
 * 全量打包时使用默认的任务，补丁打包使用此任务以提高效率(仅编译变化的java文件不去扫描代码内容)
 *
 * https://ant.apache.org/manual/Tasks/javac.html
 *
 */

public class FasterCCustomJavacTask extends DefaultTask {
    FasterCVariant fasterCVariant

    FasterCCustomJavacTask() {
        group = "fasterc"
    }

    @TaskAction
    void compile() {
        FasterCLog.error(project,"FasterCCustomJavacTask has execute........")
        //编译任务可用
        def compileTask = fasterCVariant.androidVariant.javaCompile
        compileTask.enabled = true

        def project = fasterCVariant.project
        def projectSnapshot = fasterCVariant.projectSnapshot
        File classesDir = fasterCVariant.androidVariant.getVariantData().getScope().getJavaOutputDir()
        if (!FileUtils.isLegalFile(new File(classesDir.absolutePath))) {
            return
        }
        if (!fasterCVariant.configuration.useCustomCompile) {
            return
        }
        if (!fasterCVariant.hasDexCache) {
            return
        }

        SourceSetDiffResultSet sourceSetDiffResultSet = projectSnapshot.diffResultSet
        //java文件是否发生变化
        if (!sourceSetDiffResultSet.isJavaFileChanged()) {
            FasterCLog.error(project, "no java files changed, just ignore")
            compileTask.enabled = false
            return
        }

        //此次变化是否和上次的变化一样
        if (projectSnapshot.diffResultSet != null
                && projectSnapshot.oldDiffResultSet != null
                && projectSnapshot.diffResultSet == projectSnapshot.oldDiffResultSet) {
            FasterCLog.error(project, " java files not changed, just ignore")
            compileTask.enabled = false
            return
        }

        Set<PathInfo> addOrModifiedPathInfos = sourceSetDiffResultSet.addOrModifiedPathInfos
        File patchJavaFileDir = new File(FasterCUtils.getWorkDir(project, fasterCVariant.variantName), "custom-combind")
        File patchClassesFileDir = new File(FasterCUtils.getWorkDir(project, fasterCVariant.variantName), "custom-combind-classes")
        FileUtils.ensureDir(patchJavaFileDir)
        FileUtils.ensureDir(patchClassesFileDir)
        //拷贝变化文件
        for (PathInfo pathInfo : addOrModifiedPathInfos) {
            FasterCLog.error(project, " changed java file: ${pathInfo.relativePath}")
            FileUtils.copyFile(pathInfo.absoluteFile, new File(patchJavaFileDir, pathInfo.relativePath))
        }

        //compile java
        File androidJar = new File("${FasterCUtils.getSdkDirectory(project)}${File.separator}platforms${File.separator}${project.android.getCompileSdkVersion()}${File.separator}android.jar")
        File classpathJar = FasterCUtils.getInjectedJarFile(project, fasterCVariant.variantName)

        def classpath = project.files(classpathJar.absolutePath)
        def fork = compileTask.options.fork
        def executable = compileTask.options.forkOptions.executable

        FasterCLog.error(project, " executable ${executable}")
        //处理retrolambda
        if (project.plugins.hasPlugin("me.tatarka.retrolambda")) {
            fork = true
            def retrolambda = project.retrolambda
            def rt = "$retrolambda.jdk/jre/lib/rt.jar"
            classpath = classpath + project.files(rt)
            executable = "${retrolambda.tryGetJdk()}/bin/javac"
        }
        FasterCLog.error(project, " androidJar: ${androidJar}")
        FasterCLog.error(project, " classpath: ${classpath.files}")

        //https://ant.apache.org/manual/Tasks/javac.html
        //最好检测下项目根目录的gradle.properties文件,是否有这个配置org.gradle.jvmargs=-Dfile.encoding=UTF-8
        project.ant.javac(
                srcdir: patchJavaFileDir,
                destdir: patchClassesFileDir,
                source: compileTask.sourceCompatibility,
                target: compileTask.targetCompatibility,
                encoding: 'UTF-8',
                botclasspath: androidJar,
                classpath: joinClasspath(classpath),
                fork: fork,
                executable: executable
        )

        FasterCLog.error(project, " compile success: ${patchClassesFileDir}")

        //覆盖app/build/intermediates/classes内容
        Files.walkFileTree(patchClassesFileDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = patchClassesFileDir.toPath().relativize(file)
                File destFile = new File(classesDir, relativePath.toString())
                FileUtils.copyFileUsingStream(file.toFile(), destFile)

                FasterCLog.error(project, " apply class to ${destFile}")
                return FileVisitResult.CONTINUE
            }
        })
        compileTask.enabled = false
        //保存对比信息
        fasterCVariant.projectSnapshot.saveDiffResultSet()

    }

    static StringBuilder joinClasspath(FileCollection collection) {
        StringBuilder sb = new StringBuilder()
        collection.files.each { file ->
            sb.append(file.absolutePath)
            sb.append(":")
        }
        if (sb.toString().endsWith(":")) {
            sb.deleteCharAt(sb.length() - 1)
        }
        return sb
    }

}
