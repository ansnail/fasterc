package top.dex.fasterc.fastercdex;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import top.dex.fasterc.FasterCApplication;
import top.dex.fasterc.constants.ShareConstants;
import top.dex.fasterc.decice.Server;
import top.dex.fasterc.loader.ResourcePatcher;
import top.dex.fasterc.loader.SharePatchFileUtil;
import top.dex.fasterc.loader.SystemClassLoaderAdder;
import top.dex.fasterc.utils.FileUtils;

/**
 * Created by yanjie on 2017-08-17.
 * Describe:
 */

public class FasterCdex {
    private static FasterCdex instance;

    public static FasterCdex get(Context context) {
        if (instance == null) {
            synchronized (FasterCdex.class) {
                if (instance == null) {
                    instance = new FasterCdex(context);
                }
            }
        }
        return instance;
    }

    private boolean fasterCEnabled = true;
    MetaInfo metaInfo;
    File fasterCdexDirectory;
    File patchDirectory;
    File tempDirectory;

    private Context applicationContext;

    private FasterCdex(Context applicationContext) {
        this.applicationContext = applicationContext;

        fasterCdexDirectory = SharePatchFileUtil.getFasterCDirectory(applicationContext);
        patchDirectory = SharePatchFileUtil.getPatchDirectory(applicationContext);
        tempDirectory = SharePatchFileUtil.getPatchTempDirectory(applicationContext);

        //加载编译信息
        MetaInfo metaInfo = MetaInfo.load(this);
        MetaInfo assetsMetaInfo;

        try {
            InputStream is = applicationContext.getAssets().open(ShareConstants.META_INFO_FILENAME);
            String assetsMetaInfoJson = new String(FileUtils.readStream(is));
            assetsMetaInfo = MetaInfo.load(assetsMetaInfoJson);
            if (assetsMetaInfo == null) {
                throw new NullPointerException("AssetsMetaInfo can not be null!!!");
            }
            if (metaInfo == null) {
                assetsMetaInfo.save(this);
                metaInfo = assetsMetaInfo;
                File metaInfoFile = new File(fasterCdexDirectory, ShareConstants.META_INFO_FILENAME);
                if (!FileUtils.isLegalFile(metaInfoFile)) {
                    throw new RuntimeException("save meta-info fail: " + metaInfoFile.getAbsolutePath());
                }
            } else if (!metaInfo.equals(assetsMetaInfo)) {
                FileUtils.cleanDir(fasterCdexDirectory);
                FileUtils.cleanDir(tempDirectory);
                assetsMetaInfo.save(this);
                metaInfo = assetsMetaInfo;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            fasterCEnabled = false;
        }

        this.metaInfo = metaInfo;

    }

    public void onAttachBaseContext(FasterCApplication fasterCApplication) {
        if (!fasterCEnabled) {
            return;
        }
        if (!TextUtils.isEmpty(metaInfo.getPreparedPatchPath())) {
            if (!TextUtils.isEmpty(metaInfo.getLastPatchPath())) {
                FileUtils.deleteDir(new File(metaInfo.getLastPatchPath()));
            }
            File preparedPatchDir = new File(metaInfo.getPreparedPatchPath());
            File patchDir = patchDirectory;

            FileUtils.deleteDir(patchDir);
            preparedPatchDir.renameTo(patchDir);

            metaInfo.setLastPatchPath(metaInfo.getPatchPath());
            metaInfo.setPreparedPatchPath(null);
            metaInfo.setPatchPath(patchDir.getAbsolutePath());
            metaInfo.save(this);
        }

        if (TextUtils.isEmpty(metaInfo.getPatchPath())) {
            return;
        }

        final File dexDirectory = new File(new File(metaInfo.getPatchPath()),ShareConstants.DEX_DIR);
        final File optDirectory = new File(new File(metaInfo.getPatchPath()),ShareConstants.OPT_DIR);
        final File resourceDirectory = new File(new File(metaInfo.getPatchPath()),ShareConstants.RES_DIR);
        FileUtils.ensureDir(optDirectory);
        File resourceApkFile = new File(resourceDirectory, ShareConstants.RESOURCE_APK_FILE_NAME);
        if (FileUtils.isLegalFile(resourceApkFile)) {
            try {
                ResourcePatcher.monkeyPatchExistingResources(applicationContext,resourceApkFile.getAbsolutePath());
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        File mergedPatchDex = new File(dexDirectory,ShareConstants.MERGED_PATCH_DEX);
        File patchDex = new File(dexDirectory,ShareConstants.PATCH_DEX);

        ArrayList<File> dexList = new ArrayList<>();
        if (FileUtils.isLegalFile(mergedPatchDex)) {
            dexList.add(mergedPatchDex);
        }
        if (FileUtils.isLegalFile(patchDex)) {
            dexList.add(patchDex);
        }

        if (!dexList.isEmpty()) {
            PathClassLoader classLoader = (PathClassLoader) FasterCdex.class.getClassLoader();
            try {
                SystemClassLoaderAdder.installDexes(fasterCApplication,classLoader,optDirectory,dexList);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        Server.showToast("fasterc, apply patch successful",applicationContext);
    }

    public File getTempDirectory() {
        return tempDirectory;
    }

    public MetaInfo getRuntimeMetaInfo() {
        return metaInfo;
    }

    public boolean isFasterCEnabled() {
        return fasterCEnabled;
    }
    
    
}
