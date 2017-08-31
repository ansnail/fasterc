package top.gradle.fasterc.tasks

import groovy.xml.Namespace
import groovy.xml.QName;
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.FasterCLog
import top.gradle.fasterc.variant.FasterCVariant
import top.lib.fasterc.constants.ShareConstants;

/**
 * Created by yanjie on 2017-08-17.
 * Describe:
 * 替换项目的 Application 为自定义 Application
 * 并且在Manifest文件里中添加下面的节点
 * <meta-data android:name="FASTERC_ORIGIN_APPLICATION_CLASSNAME" android:value="${项目真正的Application}"/>
 */

public class FasterCManifestTask extends DefaultTask{

    FasterCVariant fasterCVariant

    FasterCManifestTask(){
        group = 'fasterc'
    }

    @TaskAction
    def updateManifest() {
        FasterCLog.error(project,"FasterCManifestTask has execute........")
        def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")

        def xml = new XmlParser().parse(new InputStreamReader(new FileInputStream(fasterCVariant.manifestPath), "utf-8"))

        def application = xml.application[0]
        if (application) {
            QName nameAttr = new QName("http://schemas.android.com/apk/res/android", 'name', 'android');
            def applicationName = application.attribute(nameAttr)
            if (applicationName == null || applicationName.isEmpty()) {
                applicationName = "android.app.Application"
            }
            application.attributes().put(nameAttr, "top.dex.fasterc.FasterCApplication")

            def metaDataTags = application['meta-data']

            metaDataTags.findAll {
                it.attributes()[ns.name] == ShareConstants.FASTERC_ORIGIN_APPLICATION_CLASSNAME
            }.each {
                it.parent().remove(it)
            }
            // 增加新节点FASTERC_ORIGIN_APPLICATION_CLASSNAME
            application.appendNode('meta-data', [(ns.name): ShareConstants.FASTERC_ORIGIN_APPLICATION_CLASSNAME, (ns.value): applicationName])

            //重写manifest文件
            def printer = new XmlNodePrinter(new PrintWriter(fasterCVariant.manifestPath, "utf-8"))
            printer.preserveWhitespace = true
            printer.print(xml)
        }
    }
}
