package top.gradle.fasterc.variant;

import com.google.gson.Gson;

import org.gradle.api.Project
import top.gradle.fasterc.utils.FasterCUtils
import top.lib.fasterc.utils.FileUtils;

/**
 * Created by yanjie on 2017-08-16.
 * Describe:辅助信息
 */

public class MetaInfo {
    public String projectPath   //工程路径
    public String rootProjectPath   //工程根目录路径

    public String fasterCVersion
    public int dexCount //全量编译完成后输出的dex个数

    public long buildMillis //全量编译完成的时间
    public String variantName

    public int mergedDexVersion
    public int patchDexVersion


    /**
     * 是否移动了工程目录
     * @param project
     * @return
     */
    public boolean isRootProjectDirChanged(String curRootProjectPath) {
        return curRootProjectPath != rootProjectPath
    }


    //保存metainfo信息
    public void save(FasterCVariant fasterCVariant) {
        File metaInfoFile = FasterCUtils.getMetaInfoFile(fasterCVariant.project,fasterCVariant.variantName)
        SerializeUtils.serializeTo(new FileOutputStream(metaInfoFile),this)
    }

    //读取保存的metainfo信息
    public static MetaInfo load(Project project, String variantName) {
        File metaInfoFile = FasterCUtils.getMetaInfoFile(project,variantName)
        try {
            return new Gson().fromJson(new String(FileUtils.readContents(metaInfoFile)),MetaInfo.class)
        } catch (Throwable e) {
            e.printStackTrace()
            return null
        }
    }

    @Override
    public String toString() {
        return "MetaInfo{" + "buildMillis=" + buildMillis + ", variantName='" + variantName + '\'' + '}';
    }
}
