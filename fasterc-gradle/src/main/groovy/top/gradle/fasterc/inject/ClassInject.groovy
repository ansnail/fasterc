package top.gradle.fasterc.inject

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import org.objectweb.asm.*
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.variant.FasterCVariant
import top.gradle.fasterc.variant.LibDependency
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 source class:
 ''''''
 public class MainActivity {

 }
 '''''

 dest class:
 ''''''
 import top.dex.fasterc.antilazyload.AntiLazyLoad;

 public class MainActivity {
 public MainActivity() {
 System.out.println(Antilazyload.str);
 }
 }
 ''''''
 * 代码注入，往所有的构造方法中添加对top.dex.fasterc.antilazyload.AntiLazyLoad的依赖
 * Created by tong on 17/10/3.
 */
public class ClassInject implements Opcodes {
    /**
     * 注入class目录和jar文件
     * @param fasterCVariant
     * @param transformInvocation
     */
    public static void injectTransformInvocation(FasterCVariant fasterCVariant, TransformInvocation transformInvocation) {
        //所有的class目录
        HashSet<File> directoryInputFiles = new HashSet<>();
        //所有输入的jar
        HashSet<File> jarInputFiles = new HashSet<>();
        for (TransformInput input : transformInvocation.getInputs()) {
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs()
            if (directoryInputs != null) {
                for (DirectoryInput directoryInput : directoryInputs) {
                    directoryInputFiles.add(directoryInput.getFile())
                }
            }

            Collection<JarInput> jarInputs = input.getJarInputs()
            if (jarInputs != null) {
                for (JarInput jarInput : jarInputs) {
                    jarInputFiles.add(jarInput.getFile())
                }
            }
        }
        injectDirectoryInputFiles(fasterCVariant,directoryInputFiles)
        injectJarInputFiles(fasterCVariant,jarInputFiles)
    }

    /**
     * 往所有项目代码里注入解决pre-verify问题的code
     * @param directoryInputFiles
     */
    public static void injectDirectoryInputFiles(FasterCVariant fasterCVariant, HashSet<File> directoryInputFiles) {
        def project = fasterCVariant.project
        long start = System.currentTimeMillis()
        for (File classpathFile : directoryInputFiles) {
            FasterCLog.error(project,"inject dir: ${classpathFile.getAbsolutePath()}")
            ClassInject.injectDirectory(fasterCVariant,classpathFile,true)
        }
        long end = System.currentTimeMillis()
        FasterCLog.error(project,"Inject complete dir-size: ${directoryInputFiles.size()} , use: ${end - start}ms")
    }

    /**
     * 注入所有的依赖的library输出jar
     *
     * @param fasterCVariant
     * @param directoryInputFiles
     */
    public static void injectJarInputFiles(FasterCVariant fasterCVariant, HashSet<File> jarInputFiles) {
        def project = fasterCVariant.project
        long start = System.currentTimeMillis()

        Set<LibDependency> libraryDependencies = fasterCVariant.libraryDependencies
        List<File> projectJarFiles = new ArrayList<>()
        //获取所有依赖工程的输出jar (compile project(':xxx'))
        for (LibDependency dependency : libraryDependencies) {
            projectJarFiles.add(dependency.jarFile)
        }
        if (fasterCVariant.configuration.debug) {
            FasterCLog.error(project,"projectJarFiles : ${projectJarFiles}")
        }
        for (File file : jarInputFiles) {
            if (!projectJarFiles.contains(file)) {
                continue
            }
            FasterCLog.error(project,"inject jar: ${file}")
            ClassInject.injectJar(fasterCVariant,file,file)
        }
        long end = System.currentTimeMillis()
        FasterCLog.error(project,"inject complete jar-size: ${projectJarFiles.size()} , use: ${end - start}ms")
    }

    /**
     * 注入jar包
     * @param fasterCVariant
     * @param inputJar
     * @param outputJar
     * @return
     */
    public static injectJar(FasterCVariant fasterCVariant, File inputJar,File outputJar) {
        File tempDir = new File(fasterCVariant.buildDir,"temp")
        FileUtils.deleteDir(tempDir)
        FileUtils.ensureDir(tempDir)

        def project = fasterCVariant.project
        project.copy {
            from project.zipTree(inputJar)
            into tempDir
        }
        ClassInject.injectDirectory(fasterCVariant,tempDir,false)
        project.ant.zip(baseDir: tempDir, destFile: outputJar)
        FileUtils.deleteDir(tempDir)
    }

    /**
     * 注入指定目录下的所有class
     * @param classpath
     */
    public static void injectDirectory(FasterCVariant fasterCVariant,File classesDir,boolean applicationProjectSrc) {
        if (!FileUtils.dirExists(classesDir.absolutePath)) {
            return
        }
        Path classpath = classesDir.toPath()

        Files.walkFileTree(classpath,new SimpleFileVisitor<Path>(){
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                File classFile = file.toFile()
                String fileName = classFile.getName()
                if (!fileName.endsWith(ShareConstants.CLASS_SUFFIX)) {
                    return FileVisitResult.CONTINUE;
                }

                boolean needInject = true
                if (applicationProjectSrc && (fileName.endsWith("R.class") || fileName.matches("R\\\$\\S{1,}.class"))) {
                    String packageName = fasterCVariant.getOriginPackageName()
                    String packageNamePath = packageName.split("\\.").join(File.separator)
                    if (!classFile.absolutePath.endsWith("${packageNamePath}${File.separator}${fileName}")) {
                        needInject = false
                    }
                }
                if (needInject) {
                    FasterCLog.error(fasterCVariant.project,"inject path: ${classFile.getAbsolutePath()}")
                    byte[] classBytes = FileUtils.readContents(classFile)
                    classBytes = ClassInject.inject(classBytes)
                    FileUtils.write2file(classBytes,classFile)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * 往class字节码注入code
     * @param classBytes
     * @return
     */
    public static final byte[] inject(byte[] classBytes) {
        ClassReader classReader = new ClassReader(classBytes);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new MyClassVisitor(classWriter);
        classReader.accept(classVisitor, Opcodes.ASM5);

        return classWriter.toByteArray()
    }

    private static class MyClassVisitor extends ClassVisitor {
        public MyClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM5, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access,
                                         String name,
                                         String desc,
                                         String signature,
                                         String[] exceptions) {
            if ("<init>" == name) {
                MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
                MethodVisitor newMethod = new AsmMethodVisit(mv);
                return newMethod;
            } else {
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        }
    }

    static class AsmMethodVisit extends MethodVisitor {
        public AsmMethodVisit(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                super.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                Label l0 = new Label();
                super.visitJumpInsn(IFEQ, l0);
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitFieldInsn(GETSTATIC, "top/dex/fasterc/antilazyload/AntiLazyLoad", "codeStr", "Ljava/lang/String;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                super.visitLabel(l0);
            }
            super.visitInsn(opcode);
        }
    }
}
