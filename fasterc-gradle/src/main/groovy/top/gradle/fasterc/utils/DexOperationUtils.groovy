package top.gradle.fasterc.utils

import com.android.build.api.transform.Transform
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.process.ProcessOutputHandler
import org.objectweb.asm.Opcodes
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils

/**
 * dex操作
 * Created by tong on 17/11/4.
 */
public class DexOperationUtils implements Opcodes {
    /**
     * 生成补丁dex
     * @param fasterCVariant
     * @param base
     * @param patchJar
     * @param patchDex
     */
    public static
    final void generatePatchDex(FasterCVariant fasterCVariant, Transform base, File patchJar, File patchDex) {
        FileUtils.deleteFile(patchDex)
        ProcessOutputHandler outputHandler = new ParsingProcessOutputHandler(
                new ToolOutputParser(new DexParser(), Message.Kind.ERROR, base.logger),
                new ToolOutputParser(new DexParser(), base.logger),
                base.androidBuilder.getErrorReporter())
        final List<File> inputFiles = new ArrayList<>()
        inputFiles.add(patchJar)

        FileUtils.ensureDir(patchDex.parentFile)
        String androidGradlePluginVersion = GradleUtils.GRADLE_VERSION
        long start = System.currentTimeMillis()
        if ("2.0.0" == androidGradlePluginVersion) {
            base.androidBuilder.convertByteCode(
                    inputFiles,
                    patchDex.parentFile,
                    false,
                    null,
                    base.dexOptions,
                    null,
                    false,
                    true,
                    outputHandler,
                    false)
        } else if ("2.1.0" == androidGradlePluginVersion || "2.1.2" == androidGradlePluginVersion || "2.1.3" == androidGradlePluginVersion) {
            base.androidBuilder.convertByteCode(
                    inputFiles,
                    patchDex.parentFile,
                    false,
                    null,
                    base.dexOptions,
                    null,
                    false,
                    true,
                    outputHandler)
        } else if (androidGradlePluginVersion.startsWith("2.2.")) {
            base.androidBuilder.convertByteCode(
                    inputFiles,
                    patchDex.parentFile,
                    false,
                    null,
                    base.dexOptions,
                    base.getOptimize(),
                    outputHandler);
        } else if ("2.3.0" == androidGradlePluginVersion) {
            base.androidBuilder.convertByteCode(
                    inputFiles,
                    patchDex.parentFile,
                    false,
                    null,//fix-issue#27  fix-issue#22
                    base.dexOptions,
                    outputHandler)
        } else {
            // 补丁的方法数也有可能超过65535个，最好加上使dx生成多个dex的参数，但是一般补丁不会那么大所以暂时不处理
            //调用dx命令
            def process = new ProcessBuilder(FasterCUtils.getDxCmdPath(fasterCVariant.project), "--dex", "--output=${patchDex}", patchJar.absolutePath).start()
            int status = process.waitFor()
            try {
                process.destroy()
            } catch (Throwable e) {

            }
            if (status != 0) {
                //拼接生成dex的命令 project.android.getSdkDirectory()
                String dxcmd = "${FasterCUtils.getDxCmdPath(fasterCVariant.project)} --dex --output=${patchDex} ${patchJar}"
                throw new FasterCRuntimeException("==fasterc generate dex fail: \n${dxcmd}")
            }
        }

        long end = System.currentTimeMillis();
        FasterCLog.error(fasterCVariant.project, "patch transform generate dex success: \n==${patchDex} use: ${end - start}ms")
    }

    /**
     * 合并dex
     * @param fasterCVariant
     * @param outputDex 输出的dex路径
     * @param patchDex 补丁dex路径
     * @param cachedDex
     */
    public
    static void mergeDex(FasterCVariant fasterCVariant, File outputDex, File patchDex, File cachedDex) {
        long start = System.currentTimeMillis()
        def project = fasterCVariant.project
        File dexMergeCommandJarFile = new File(FasterCUtils.getBuildDir(project), ShareConstants.DEX_MERGE_JAR_FILENAME)
        if (!FileUtils.isLegalFile(dexMergeCommandJarFile)) {
            FileUtils.copyResourceUsingStream(ShareConstants.DEX_MERGE_JAR_FILENAME, dexMergeCommandJarFile)
        }

        String javaCmdPath = FasterCUtils.getJavaCmdPath()
        def process = new ProcessBuilder(javaCmdPath, "-jar", dexMergeCommandJarFile.absolutePath, outputDex.absolutePath, patchDex.absolutePath, cachedDex.absolutePath).start()
        int status = process.waitFor()
        try {
            process.destroy()
        } catch (Throwable e) {

        }
        if (status != 0) {
            String cmd = "${javaCmdPath} -jar ${dexMergeCommandJarFile} ${outputDex} ${patchDex} ${cachedDex}"
            throw new FasterCRuntimeException("merge dex fail: \n${cmd}")
        }
        long end = System.currentTimeMillis();
        FasterCLog.error(project, "merge dex success: \n==${outputDex} use: ${end - start}ms")
    }
}
