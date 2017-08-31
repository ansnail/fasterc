package top.gradle.fasterc.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.utils.FasterCUtils
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.aapt.AaptResourceCollector
import top.lib.fasterc.aapt.RDotTxtEntry
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils;
import top.lib.fasterc.aapt.PatchUtil;
import top.lib.fasterc.aapt.AaptUtil;

/**
 * Created by yanjie on 2017-08-18.
 * Describe:保持补丁打包时R文件中相同的节点和第一次打包时的值保持一致
 *
 * 把第一次打包时生成的build/intermediates/symbols/${variant}/R.txt保存下来，
 * 补丁打包时使用R.txt作为输入生成public.xml和ids.xml并放进build/intermediates/res/merged/${variant}/values里面
 *
 */

public class FasterCResourceIdTask extends DefaultTask {
    FasterCVariant fasterCVariant
    String resDir

    FasterCResourceIdTask() {
        group = 'fasterc'
    }

    @TaskAction
    def applyResourceId() {
        FasterCLog.error(project,"FasterCResourceIdTask has execute........")
        //判断是否有备份文件
        String resourceMappingFile = FasterCUtils.getResourceMappingFile(project, fasterCVariant.variantName)
        if (!FileUtils.isLegalFile(new File(resourceMappingFile))) {
            FasterCLog.error(fasterCVariant.project, "${resourceMappingFile} is illegal, just ignore")
            return
        }

        File idsXmlFile = FasterCUtils.getIdxXmlFile(project, fasterCVariant.variantName)
        File publicXmlFile = FasterCUtils.getPublicXmlFile(project, fasterCVariant.variantName)

        String idsXml = resDir + "/values/ids.xml";
        String publicXml = resDir + "/values/public.xml";
        File resDirIdsXmlFile = new File(idsXml)
        File resDirPublicXmlFile = new File(publicXml)

        //把保存的ids.xml和 public.xml 拷贝到Google编译插件需要的位置
        if (FileUtils.isLegalFile(idsXmlFile) && FileUtils.isLegalFile(publicXmlFile)) {
            if (!FileUtils.isLegalFile(resDirIdsXmlFile) || idsXmlFile.lastModified() != resDirIdsXmlFile.lastModified()) {
                FileUtils.copyFile(idsXmlFile, resDirIdsXmlFile)
                FasterCLog.error(fasterCVariant.project, "fasterc apply cached resource idx.xml ${idsXml}")
            }

            if (!FileUtils.isLegalFile(resDirPublicXmlFile) || publicXmlFile.lastModified() != resDirPublicXmlFile.lastModified()) {
                FileUtils.copyFile(publicXmlFile, resDirPublicXmlFile)
                FasterCLog.error(fasterCVariant.project, "fasterc apply cached resource public.xml ${publicXml}")
            }
            return
        }

        FileUtils.deleteFile(idsXml);
        FileUtils.deleteFile(publicXml);
        List<String> resourceDirectoryList = new ArrayList<String>()
        resourceDirectoryList.add(resDir)


        FasterCLog.error(fasterCVariant.project, "fasterc we build ${project.getName()} apk with apply resource mapping file ${resourceMappingFile}")
        Map<RDotTxtEntry.RType, Set<RDotTxtEntry>> rTypeResourceMap = PatchUtil.readRTxt(resourceMappingFile)

        AaptResourceCollector aaptResourceCollector = AaptUtil.collectResource(resourceDirectoryList, rTypeResourceMap)
        PatchUtil.generatePublicResourceXml(aaptResourceCollector, idsXml, publicXml)

        File publicFile = new File(publicXml)
        if (publicFile.exists()) {
            FileUtils.copyFile(publicFile, publicXmlFile)
            FasterCLog.error(fasterCVariant.project, "fasterc gen resource public.xml in ${ShareConstants.RESOURCE_PUBLIC_XML}")
        }
        File idxFile = new File(idsXml)
        if (idxFile.exists()) {
            FileUtils.copyFile(idxFile, idsXmlFile)
            FasterCLog.error(fasterCVariant.project, "fasterc gen resource idx.xml in ${ShareConstants.RESOURCE_IDX_XML}")
        }

    }


}
