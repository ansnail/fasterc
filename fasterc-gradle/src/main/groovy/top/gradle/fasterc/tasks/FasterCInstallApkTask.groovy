package top.gradle.fasterc.tasks

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import top.gradle.fasterc.utils.*
import top.gradle.fasterc.variant.FasterCVariant
import top.gradle.fasterc.variant.MetaInfo
import top.lib.fasterc.device.Communicator
import top.lib.fasterc.device.ServiceCommunicator
import top.lib.fasterc.utils.FileUtils

import java.awt.*
import java.util.List

/**
 * Created by yanjie on 2017-08-16.
 * Describe:当生成apk后开始安装
 */

public class FasterCInstallApkTask extends DefaultTask{
    FasterCVariant fasterCVariant
    boolean alreadySendPatch
    IDevice device

    FasterCInstallApkTask() {
        group = 'fasterc'
    }

    /**
     * 当dex生成后所做的操作
     */
    public void onDexTransformComplete() {
        if (!isInstantRunBuild()) {
            return
        }
        preparedDevice()
        def packageName = fasterCVariant.getMergedPackageName()
        ServiceCommunicator serviceCommunicator = new ServiceCommunicator(packageName)
        try {
            boolean active = false
            int appPid = -1
            MetaInfo runtimeMetaInfo = serviceCommunicator.talkToService(device, new Communicator<MetaInfo>() {
                @Override
                public MetaInfo communicate(DataInputStream input, DataOutputStream output) throws IOException {
                    output.writeInt(ProtocolConstants.MESSAGE_PING)
                    MetaInfo runtimeMetaInfo = new MetaInfo()
                    active = input.readBoolean()
                    runtimeMetaInfo.buildMillis = input.readLong()
                    runtimeMetaInfo.variantName = input.readUTF()
                    appPid = input.readInt()
                    return runtimeMetaInfo
                }
            })
            FasterCLog.error(project,"fasterc receive: ${runtimeMetaInfo}")
            if (fasterCVariant.metaInfo.buildMillis != runtimeMetaInfo.buildMillis) {
                throw new FasterCRuntimeException("buildMillis not equal")
            }
            if (fasterCVariant.metaInfo.variantName != runtimeMetaInfo.variantName) {
                throw new FasterCRuntimeException("variantName not equal")
            }

            File resourcesApk = FasterCUtils.getResourcesApk(project,fasterCVariant.variantName)
            generateResourceApk(resourcesApk)
            File mergedPatchDex = FasterCUtils.getMergedPatchDex(fasterCVariant.project,fasterCVariant.variantName)
            File patchDex = FasterCUtils.getPatchDexFile(fasterCVariant.project,fasterCVariant.variantName)

            int changeCount = 1
            if (FileUtils.isLegalFile(mergedPatchDex)) {
                changeCount += 1
            }
            if (FileUtils.isLegalFile(patchDex)) {
                changeCount += 1
            }

            long start = System.currentTimeMillis()

            serviceCommunicator.talkToService(device, new Communicator<Boolean>() {
                @Override
                public Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                    output.writeInt(ProtocolConstants.MESSAGE_PATCHES)
                    output.writeLong(0L)
                    output.writeInt(changeCount)

                    FasterCLog.error(project,"fasterc write ${ShareConstants.RESOURCE_APK_FILE_NAME}")
                    output.writeUTF(ShareConstants.RESOURCE_APK_FILE_NAME)
                    byte[] bytes = FileUtils.readContents(resourcesApk)
                    output.writeInt(bytes.length)
                    output.write(bytes)
                    if (FileUtils.isLegalFile(mergedPatchDex)) {
                        FasterCLog.error(project,"fasterc write ${mergedPatchDex}")
                        output.writeUTF(ShareConstants.MERGED_PATCH_DEX)
                        bytes = FileUtils.readContents(mergedPatchDex)
                        output.writeInt(bytes.length)
                        output.write(bytes)
                    }
                    if (FileUtils.isLegalFile(patchDex)) {
                        FasterCLog.error(project,"fasterc write ${patchDex}")
                        output.writeUTF(ShareConstants.PATCH_DEX)
                        bytes = FileUtils.readContents(patchDex)
                        output.writeInt(bytes.length)
                        output.write(bytes)
                    }

                    output.writeInt(ProtocolConstants.UPDATE_MODE_WARM_SWAP)
                    output.writeBoolean(true)

                    return input.readBoolean()
                }
            })
            long end = System.currentTimeMillis();
            FasterCLog.error(project,"fasterc send patch data success. use: ${end - start}ms")

            //kill app
            killApp(appPid)
            startBootActivity()

            project.tasks.getByName("package${fasterCVariant.variantName}").enabled = false
            project.tasks.getByName("assemble${fasterCVariant.variantName}").enabled = false
            alreadySendPatch = true
        } catch (IOException e) {
            if (fasterCVariant.configuration.debug) {
                e.printStackTrace()
            }
        }
    }

    @TaskAction
    void instantRun() {
        FasterCLog.error(project,"FasterCInstallApkTask has execute........")
        if (alreadySendPatch) {
            return
        }

        preparedDevice()
        normalRun(device)
    }

    def killApp(appPid) {
        if (appPid == -1) {
            return
        }
        //$ adb shell kill {appPid}
        def process = new ProcessBuilder(FasterCUtils.getAdbCmdPath(project),"shell","kill","${appPid}").start()
        int status = process.waitFor()
        try {
            process.destroy()
        } catch (Throwable e) {
            e.printStackTrace()
        }

        String cmd = "adb shell kill ${appPid}"
        if (fasterCVariant.configuration.debug) {
            FasterCLog.error(project,"${cmd}")
        }
        if (status != 0) {
            throw new FasterCRuntimeException("fasterc kill app fail: \n${cmd}")
        }
    }

    def startBootActivity() {
        def packageName = fasterCVariant.getMergedPackageName()

        //启动第一个activity
        String bootActivityName = GradleUtils.getBootActivity(fasterCVariant.manifestPath)
        if (bootActivityName) {
            //$ adb shell am start -n "com.dx168.fasterc.sample/com.dx168.fasterc.sample.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
            def process = new ProcessBuilder(FasterCUtils.getAdbCmdPath(project),"shell","am","start","-n","\"${packageName}/${bootActivityName}\"","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER").start()
            int status = process.waitFor()
            try {
                process.destroy()
            } catch (Throwable e) {
                e.printStackTrace()
            }

            String cmd = "adb shell am start -n \"${packageName}/${bootActivityName}\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            if (fasterCVariant.configuration.debug) {
                project.logger.error("${cmd}")
            }
            if (status != 0) {
                throw new FontFormatException("fasterc start activity fail: \n${cmd}")
            }
        }
    }

    def generateResourceApk(File resourcesApk) {
        long start = System.currentTimeMillis()
        File tempDir = new File(FasterCUtils.getResourceDir(project,fasterCVariant.variantName),"temp")
        FileUtils.cleanDir(tempDir)
        File resourceAp = new File(project.buildDir,"intermediates${File.separator}res${File.separator}resources-debug.ap_")

        File tempResourcesApk = new File(tempDir,resourcesApk.getName())
        FileUtils.copyFile(resourceAp,tempResourcesApk)

        File assetsPath = fasterCVariant.androidVariant.getVariantData().getScope().getMergeAssetsOutputDir()
        List<String> assetFiles = getAssetFiles(assetsPath)
        if (assetFiles.isEmpty()) {
            return
        }
        File tempAssetsPath = new File(tempDir,"assets")
        FileUtils.copyDir(assetsPath,tempAssetsPath)

        String[] cmds = new String[assetFiles.size() + 4]
        cmds[0] = FasterCUtils.getAaptCmdPath(project)
        cmds[1] = "add"
        cmds[2] = "-f"
        cmds[3] = tempResourcesApk.absolutePath
        for (int i = 0; i < assetFiles.size(); i++) {
            cmds[4 + i] = "assets/${assetFiles.get(i)}";
        }

        ProcessBuilder aaptProcess = new ProcessBuilder(cmds)
        aaptProcess.directory(tempDir)
        def process = aaptProcess.start()
        int status = process.waitFor()
        try {
            process.destroy()
        } catch (Throwable e) {
            e.printStackTrace();
        }

        tempResourcesApk.renameTo(resourcesApk)
        def cmd = cmds.join(" ")
        if (fasterCVariant.configuration.debug) {
            FasterCLog.error(project,"fasterc add asset files into resources.apk. cmd:\n${cmd}")
        }
        if (status != 0) {
            throw new RuntimeException("fasterc add asset files into resources.apk fail. cmd:\n${cmd}")
        }
        long end = System.currentTimeMillis();
        FasterCLog.error(project,"fasterc generate resources.apk success: \n==${resourcesApk} use: ${end - start}ms")
    }

    static List<String> getAssetFiles(File dir) {
        ArrayList<String> result = new ArrayList<>()
        if (dir == null || !FileUtils.dirExists(dir.getAbsolutePath())) {
            return result
        }
        if (dir.listFiles().length == 0) {
            return result
        }
        for (File file : dir.listFiles()) {
            if (file.isFile() && !file.getName().startsWith(".")) {
                result.add(file.getName())
            }
        }
        return result;
    }

    def isInstantRunBuild() {
        String launchTaskName = project.gradle.startParameter.taskRequests.get(0).args.get(0).toString()
        boolean result = launchTaskName.endsWith("fasterc${fasterCVariant.variantName}")
        if (fasterCVariant.configuration.debug) {
            FasterCLog.error(project,"fasterc launchTaskName: ${launchTaskName}")
        }
        return result
    }

    /**
     * 等待设备有反应
     */
    private static void waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 300) {
                throw new FasterCRuntimeException("Connect adb timeout!!")
            }
        }
    }

    /**
     * 准备设备
     */
    def preparedDevice() {
        if (device != null) {
            return
        }
        AndroidDebugBridge.initIfNeeded(false)
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(FasterCUtils.getAdbCmdPath(project), false)
        waitForDevice(bridge)
        IDevice[] devices = bridge.getDevices()
        if (devices != null && devices.length > 0) {
            if (devices.length > 1) {
                throw new FasterCRuntimeException("Find multiple devices!!")
            }
            device = devices[0]
        }

        if (device == null) {
            throw new FasterCRuntimeException("Device not found!!")
        }
        FasterCLog.error(project,"fasterc device connected ${device.toString()}")
    }

    /**
     * 正常安装
     */
    void normalRun(IDevice device) {
        def targetVariant = fasterCVariant.androidVariant
        FasterCLog.error(project,"fasterc normal run ${fasterCVariant.variantName}")
        //安装app
        File apkFile = targetVariant.outputs.first().getOutputFile()
        FasterCLog.error(project,"adb install -r ${apkFile}")
        device.installPackage(apkFile.absolutePath,true)
        startBootActivity()
    }
    

}
