package top.gradle.fasterc.utils

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.builder.model.Version
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import groovy.xml.QName
import org.gradle.api.Project
import top.lib.fasterc.constants.ShareConstants
import top.lib.fasterc.utils.FileUtils

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Created by yanjie on 2017-08-16.
 * Describe:主要用于gradle相关类的操作工具
 */

public class GradleUtils {
    public static final String GRADLE_VERSION = Version.ANDROID_GRADLE_PLUGIN_VERSION

    /**
     * 获取指定variant的依赖列表
     * @param project
     * @param applicationVariant
     * @return
     */
    public static Set<String> getCurrentDependList(Project project, Object applicationVariant) {
        String buildTypeName = applicationVariant.getBuildType().buildType.getName()
        Set<String> result = new HashSet<>()

        project.configurations.compile.each { File file ->
            result.add(file.getAbsolutePath())
        }

        project.configurations."${buildTypeName}Compile".each { File file ->
            result.add(file.getAbsolutePath())
        }
        return result
    }

    /**
     * 获取transformClassesWithDexFor${variantName}任务的dex输出目录
     * @param transformInvocation
     * @return
     */
    public static File getDexOutputDir(Transform realTransform, TransformInvocation transformInvocation) {
        def outputProvider = transformInvocation.getOutputProvider()
        if (GRADLE_VERSION.startsWith("2.4.")) {
            return outputProvider.getContentLocation("main",realTransform.getOutputTypes(),TransformManager.SCOPE_FULL_PROJECT,Format.DIRECTORY)
        }

        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> directoryInputs = Lists.newArrayList();
        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        if (GRADLE_VERSION < "2.3.0") {
            //2.3.0以前的版本
            if ((jarInputs.size() + directoryInputs.size()) == 1 || !realTransform.dexOptions.getPreDexLibraries()) {
                return outputProvider.getContentLocation("main",
                        realTransform.getOutputTypes(), realTransform.getScopes(),
                        Format.DIRECTORY);
            }
            else {
                return outputProvider.getContentLocation("main",
                        TransformManager.CONTENT_DEX, realTransform.getScopes(),
                        Format.DIRECTORY);
            }
        }
        else {
            //2.3.0以后的版本包括2.3.0
            if ((jarInputs.size() + directoryInputs.size()) == 1 || !realTransform.dexOptions.getPreDexLibraries()) {
                return outputProvider.getContentLocation("main",
                        realTransform.getOutputTypes(),
                        TransformManager.SCOPE_FULL_PROJECT,
                        Format.DIRECTORY);
            }
            else {
                return outputProvider.getContentLocation("main",
                        TransformManager.CONTENT_DEX, TransformManager.SCOPE_FULL_PROJECT,
                        Format.DIRECTORY);
            }
        }
    }

    /**
     * 获取AndroidManifest.xml文件package属性值
     * @param manifestPath
     * @return
     */
    public static String getPackageName(String manifestPath) {
        def xml = new XmlParser().parse(new InputStreamReader(new FileInputStream(manifestPath), "utf-8"))
        String packageName = xml.attribute('package')
        return packageName
    }

    /**
     * 获取启动的activity
     * @param manifestPath
     * @return
     */
    public static String getBootActivity(String manifestPath) {
        def bootActivityName = ""
        def xml = new XmlParser().parse(new InputStreamReader(new FileInputStream(manifestPath), "utf-8"))
        def application = xml.application[0]

        if (application) {
            def activities = application.activity
            QName androidNameAttr = new QName("http://schemas.android.com/apk/res/android", 'name', 'android');

            try {
                activities.each { activity->
                    def activityName = activity.attribute(androidNameAttr)

                    if (activityName) {
                        def intentFilters = activity."intent-filter"
                        if (intentFilters) {
                            intentFilters.each { intentFilter->
                                def actions = intentFilter.action
                                def categories = intentFilter.category
                                if (actions && categories) {
                                    boolean hasMainAttr = false
                                    boolean hasLauncherAttr = false

                                    actions.each { action ->
                                        def attr = action.attribute(androidNameAttr)
                                        if ("android.intent.action.MAIN" == attr.toString()) {
                                            hasMainAttr = true
                                        }
                                    }

                                    categories.each { categoriy ->
                                        def attr = categoriy.attribute(androidNameAttr)
                                        if ("android.intent.category.LAUNCHER" == attr.toString()) {
                                            hasLauncherAttr = true
                                        }
                                    }
                                    if (hasMainAttr && hasLauncherAttr) {
                                        bootActivityName = activityName
                                        throw new FasterCRuntimeException()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
        return bootActivityName
    }

    /**
     * 合并所有的代码到一个jar包
     * @param project
     * @param transformInvocation
     * @param outputJar             输出路径
     */
    public static void executeMerge(Project project,TransformInvocation transformInvocation, File outputJar) {
        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> dirInputs = Lists.newArrayList();

        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            dirInputs.addAll(input.getDirectoryInputs());
        }

        JarMerger jarMerger = getClassJarMerger(outputJar)
        jarInputs.each { jar ->
            FasterCLog.error(project,"fasterc merge jar " + jar.getFile())
            jarMerger.addJar(jar.getFile())
        }
        dirInputs.each { dir ->
            FasterCLog.error(project,"fasterc merge dir " + dir)
            jarMerger.addFolder(dir.getFile())
        }
        jarMerger.close()
        if (!FileUtils.isLegalFile(outputJar)) {
            throw new FasterCRuntimeException("merge jar fail: \n jarInputs: ${jarInputs}\n dirInputs: ${dirInputs}\n mergedJar: ${outputJar}")
        }
        FasterCLog.error(project,"fasterc merge jar success: ${outputJar}")
    }

    private static JarMerger getClassJarMerger(File jarFile) {
        JarMerger jarMerger = new JarMerger(jarFile)

        Class<?> zipEntryFilterClazz
        try {
            zipEntryFilterClazz = Class.forName("com.android.builder.packaging.ZipEntryFilter")
        } catch (Throwable t) {
            zipEntryFilterClazz = Class.forName("com.android.builder.signing.SignedJarBuilder\$IZipEntryFilter")
        }

        Class<?>[] classArr = new Class[1];
        classArr[0] = zipEntryFilterClazz
        InvocationHandler handler = new InvocationHandler(){
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                return args[0].endsWith(ShareConstants.CLASS_SUFFIX);
            }
        };
        Object proxy = Proxy.newProxyInstance(zipEntryFilterClazz.getClassLoader(), classArr, handler);

        jarMerger.setFilter(proxy);

        return jarMerger
    }

    public static TransformInvocation createNewTransformInvocation(Transform transform,TransformInvocation transformInvocation,File inputJar) {
        TransformInvocationBuilder builder = new TransformInvocationBuilder(transformInvocation.getContext());
        builder.addInputs(jarFileToInputs(transform,inputJar))
        builder.addOutputProvider(transformInvocation.getOutputProvider())
        builder.addReferencedInputs(transformInvocation.getReferencedInputs())
        builder.addSecondaryInputs(transformInvocation.getSecondaryInputs())
        builder.setIncrementalMode(transformInvocation.isIncremental())

        return builder.build()
    }

    /**
     * 改变TransformInputs的jar文件
     */
    private static Collection<TransformInput> jarFileToInputs(Transform transform,File jarFile) {
        TransformInput transformInput = new TransformInput() {
            @Override
            Collection<JarInput> getJarInputs() {
                JarInput jarInput = new JarInput() {
                    @Override
                    Status getStatus() {
                        return Status.ADDED
                    }

                    @Override
                    String getName() {
                        return jarFile.getName().substring(0, jarFile.getName().length() - ".jar".length())
                    }

                    @Override
                    File getFile() {
                        return jarFile
                    }

                    @Override
                    Set<QualifiedContent.ContentType> getContentTypes() {
                        return transform.getInputTypes()
                    }

                    @Override
                    Set<QualifiedContent.Scope> getScopes() {
                        return transform.getScopes()
                    }
                }
                return ImmutableList.of(jarInput)
            }


            @Override
            Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of()
            }
        }
        return ImmutableList.of(transformInput)
    }
}
