package top.gradle.fasterc.transform

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.utils.FileUtils
import top.gradle.fasterc.inject.ClassInject
import top.gradle.fasterc.utils.JarOperationUtils

/**
 * 拦截transformClassesWithJarMergingFor${variantName}任务,
 */
class FasterCJarMergingTransform extends TransformProxy {
    FasterCVariant fasterCVariant

    FasterCJarMergingTransform(Transform base, FasterCVariant fasterCVariant) {
        super(base)
        this.fasterCVariant = fasterCVariant
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
        if (fasterCVariant.hasDexCache) {
            if (fasterCVariant.projectSnapshot.diffResultSet.isJavaFileChanged()) {
                //补丁jar
                File patchJar = getCombinedJarFile(transformInvocation)
                //生成补丁jar
                JarOperationUtils.generatePatchJar(fasterCVariant,transformInvocation,patchJar)
            }
            else {
                FasterCLog.error(fasterCVariant.project,"no java files have changed, just ignore")
            }
        }
        else {
            //inject dir input
            ClassInject.injectTransformInvocation(fasterCVariant,transformInvocation)
            FasterCLog.error(fasterCVariant.project,"===jar====ok===")
            base.transform(transformInvocation)
        }

        fasterCVariant.executedJarMerge = true
    }

    /**
     * 获取输出jar路径
     * @param invocation
     * @return
     */
    public File getCombinedJarFile(TransformInvocation invocation) {
        def outputProvider = invocation.getOutputProvider();

        // all the output will be the same since the transform type is COMBINED.
        // and format is SINGLE_JAR so output is a jar
        File jarFile = outputProvider.getContentLocation("combined", base.getOutputTypes(), base.getScopes(), Format.JAR);
        FileUtils.ensureDir(jarFile.getParentFile());
        return jarFile
    }
}