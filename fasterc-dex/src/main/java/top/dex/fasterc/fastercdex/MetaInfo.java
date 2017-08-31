package top.dex.fasterc.fastercdex;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import top.dex.fasterc.utils.FileUtils;
import top.dex.fasterc.utils.SerializeUtils;

import static top.dex.fasterc.constants.ShareConstants.META_INFO_FILENAME;


/**
 * Created by yanjie on 2017-08-16.
 * Describe:辅助信息
 */

public class MetaInfo {

    /**
     * 全量编译完成的时间
     */
    private long buildMillis;

    private String variantName;

    private String lastPatchPath;

    private String patchPath;

    private String preparedPatchPath;

    public long getBuildMillis() {
        return buildMillis;
    }

    public void setBuildMillis(long buildMillis) {
        this.buildMillis = buildMillis;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getLastPatchPath() {
        return lastPatchPath;
    }

    public void setLastPatchPath(String lastPatchPath) {
        this.lastPatchPath = lastPatchPath;
    }

    public String getPatchPath() {
        return patchPath;
    }

    public void setPatchPath(String patchPath) {
        this.patchPath = patchPath;
    }

    public String getPreparedPatchPath() {
        return preparedPatchPath;
    }

    public void setPreparedPatchPath(String preparedPatchPath) {
        this.preparedPatchPath = preparedPatchPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MetaInfo metaInfo = (MetaInfo) o;

        if (buildMillis != metaInfo.buildMillis) return false;
        return variantName != null ? variantName.equals(metaInfo.variantName) : metaInfo.variantName == null;

    }

    @Override
    public int hashCode() {
        int result = (int) (buildMillis ^ (buildMillis >>> 32));
        result = 31 * result + (variantName != null ? variantName.hashCode() : 0);
        return result;
    }

    public void save(FasterCdex fasterCdex) {
        File metaInfoFile = new File(fasterCdex.fasterCdexDirectory, META_INFO_FILENAME);
        try {
            SerializeUtils.serializeTo(metaInfoFile,this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MetaInfo load(FasterCdex fasterCdex) {
        File metaInfoFile = new File(fasterCdex.fasterCdexDirectory, META_INFO_FILENAME);
        try {
            return new MetaInfo();
//            return new Gson().fromJson(new String(FileUtils.readStream(metaInfoFile)),MetaInfo.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static MetaInfo load(InputStream is) {
        try {
            return new Gson().fromJson(new String(FileUtils.readStream(is)),MetaInfo.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static MetaInfo load(String json) {
        try {
            return new Gson().fromJson(json,MetaInfo.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }
}
