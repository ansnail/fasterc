package top.gradle.fasterc.utils

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import org.apache.tools.ant.taskdefs.condition.Os
import org.objectweb.asm.Opcodes
import top.gradle.fasterc.inject.ClassInject
import top.gradle.fasterc.variant.FasterCVariant
import top.gradle.fasterc.variant.LibDependency
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.snapshot.api.DiffResultSet
import top.lib.fasterc.utils.FileUtils

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * jar操作
 */
public class JarOperationUtils implements Opcodes {
    public static void generatePatchJar(FasterCVariant fasterCVariant, TransformInvocation transformInvocation, File patchJar) throws IOException {
        Set<LibDependency> libraryDependencies = fasterCVariant.libraryDependencies
        Map<String,String> jarAndProjectPathMap = new HashMap<>()
        List<File> projectJarFiles = new ArrayList<>()
        //获取所有依赖工程的输出jar (compile project(':xxx'))
        for (LibDependency dependency : libraryDependencies) {
            projectJarFiles.add(dependency.jarFile)
            jarAndProjectPathMap.put(dependency.jarFile.absolutePath,dependency.dependencyProject.projectDir.absolutePath)
        }

        //所有的class目录
        Set<File> directoryInputFiles = new HashSet<>();
        //所有输入的jar
        Set<File> jarInputFiles = new HashSet<>();
        for (TransformInput input : transformInvocation.getInputs()) {
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs()
            if (directoryInputs != null) {
                for (DirectoryInput directoryInput : directoryInputs) {
                    directoryInputFiles.add(directoryInput.getFile())
                }
            }

            if (!projectJarFiles.isEmpty()) {
                Collection<JarInput> jarInputs = input.getJarInputs()
                if (jarInputs != null) {
                    for (JarInput jarInput : jarInputs) {
                        if (projectJarFiles.contains(jarInput.getFile())) {
                            jarInputFiles.add(jarInput.getFile())
                        }
                    }
                }
            }
        }

        def project = fasterCVariant.project
        File tempDir = new File(fasterCVariant.buildDir,"temp")
        FileUtils.deleteDir(tempDir)
        FileUtils.ensureDir(tempDir)

        Set<File> moudleDirectoryInputFiles = new HashSet<>()
        DiffResultSet diffResultSet = fasterCVariant.projectSnapshot.diffResultSet
        for (File file : jarInputFiles) {
            String projectPath = jarAndProjectPathMap.get(file.absolutePath)
            List<String> patterns = diffResultSet.addOrModifiedClassesMap.get(projectPath)
            if (patterns != null && !patterns.isEmpty()) {
                File classesDir = new File(tempDir,"${file.name}-${System.currentTimeMillis()}")
                project.copy {
                    from project.zipTree(file)
                    for (String pattern : patterns) {
                        include pattern
                    }
                    into classesDir
                }
                moudleDirectoryInputFiles.add(classesDir)
                directoryInputFiles.add(classesDir)
            }
        }
        generatePatchJar(fasterCVariant,directoryInputFiles,moudleDirectoryInputFiles,patchJar);
    }

    /**
     * 生成补丁jar,仅把变化部分参与jar的生成
     * @param project
     * @param directoryInputFiles
     * @param outputJar
     * @param changedClassPatterns
     * @throws IOException
     */
    public static void generatePatchJar(FasterCVariant fasterCVariant, Set<File> directoryInputFiles, Set<File> moudleDirectoryInputFiles, File patchJar) throws IOException {
        long start = System.currentTimeMillis()
        def project = fasterCVariant.project
        FasterCLog.error(project,"11111111111111111111111111111generate patch jar start")

        if (directoryInputFiles == null || directoryInputFiles.isEmpty()) {
            throw new IllegalArgumentException("DirectoryInputFiles can not be null!!")
        }

        Set<String> changedClasses = fasterCVariant.projectSnapshoot.diffResultSet.addOrModifiedClasses
        if (fasterCVariant.configuration.hotClasses != null && fasterCVariant.configuration.hotClasses.length > 0) {
            String packageName = fasterCVariant.getOriginPackageName()
            for (String str : fasterCVariant.configuration.hotClasses) {
                if (str != null) {
                    changedClasses.add(str.replaceAll("\\{package\\}",packageName))
                }
            }
        }

        if (changedClasses == null || changedClasses.isEmpty()) {
            throw new IllegalArgumentException("No java files changed!!")
        }

        FileUtils.deleteFile(patchJar)

        boolean willExeDexMerge = fasterCVariant.willExecDexMerge()

        ZipOutputStream outputJarStream = null
        try {
            outputJarStream = new ZipOutputStream(new FileOutputStream(patchJar));
            for (File classpathFile : directoryInputFiles) {
                Path classpath = classpathFile.toPath()

                boolean skip = (moudleDirectoryInputFiles != null && moudleDirectoryInputFiles.contains(classpathFile))

                Files.walkFileTree(classpath,new SimpleFileVisitor<Path>(){
                    @Override
                    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!file.toFile().getName().endsWith(ShareConstants.CLASS_SUFFIX)) {
                            return FileVisitResult.CONTINUE;
                        }
                        Path relativePath = classpath.relativize(file)
                        String entryName = relativePath.toString()
                        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                            entryName = entryName.replace("\\", "/");
                        }

                        if (skip) {
                            ZipEntry e = new ZipEntry(entryName)
                            outputJarStream.putNextEntry(e)
                            byte[] bytes = FileUtils.readContents(file.toFile())
                            //如果需要触发dex merge,必须注入代码
                            if (willExeDexMerge) {
//                                injectClassFile(file.toFile())
                                bytes = ClassInject.injectTest(bytes)
                                FasterCLog.error(project," inject: ${entryName}")
                            }
                            outputJarStream.write(bytes,0,bytes.length)
                            outputJarStream.closeEntry()
                        }else {
                            String className = relativePath.toString().substring(0,relativePath.toString().length() - ShareConstants.CLASS_SUFFIX.length());
                            className = className.replaceAll(Os.isFamily(Os.FAMILY_WINDOWS) ? "\\\\" : File.separator,"\\.")
                            for (String cn : changedClasses) {
                                if (cn == className || className.startsWith("${cn}\$")) {

                                    ZipEntry e = new ZipEntry(entryName)
                                    outputJarStream.putNextEntry(e)

                                    byte[] bytes = FileUtils.readContents(file.toFile())
                                    //如果需要触发dex merge,必须注入代码
                                    if (willExeDexMerge) {
                                        bytes = ClassInject.injectTest(bytes)
                                        FasterCLog.error(project," inject: ${entryName}")
                                    }
                                    outputJarStream.write(bytes,0,bytes.length)
                                    outputJarStream.closeEntry()
                                    break;
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            }

        } finally {
            if (outputJarStream != null) {
                outputJarStream.close();
            }
        }

        if (!FileUtils.isLegalFile(patchJar)) {
            throw new FasterCRuntimeException("generate patch jar fail: ${patchJar}")
        }
        long end = System.currentTimeMillis();
        FasterCLog.error(project,"generate patch jar complete: ${patchJar} use: ${end - start}ms")
    }
}
