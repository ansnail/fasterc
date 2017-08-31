package top.lib.fasterc.constants;

/**
 * 全局常量数据
 */
public interface ShareConstants {
    String JAVA_SUFFIX = ".java";
    String CLASS_SUFFIX = ".class";
    String DEX_SUFFIX = ".dex";
    String CLASSES = "classes";
    String CLASSES_DEX = CLASSES + DEX_SUFFIX;
    String META_INFO_FILENAME = "fasterc-meta-info.json";
    String RESOURCE_APK_FILE_NAME = "resources.apk";
    String MERGED_PATCH_DEX = "merged-patch.dex";
    String PATCH_DEX = "patch.dex";

    String fasterc_DIR = "fasterc";
    String PATCH_DIR = "patch";
    String TEMP_DIR = "temp";
    String DEX_DIR = "dex";
    String OPT_DIR = "opt";
    String RES_DIR = "res";
    String FASTERC_ORIGIN_APPLICATION_CLASSNAME = "FASTERC_ORIGIN_APPLICATION_CLASSNAME";

    String MIN_SUPPORT_ANDROID_GRADLE_VERSION = "2.0.0";
    String BUILD_DIR = "fasterc";
    String INJECTED_JAR_FILENAME = "injected-combined.jar";
    String R_TXT = "r.txt";
    String RESOURCE_PUBLIC_XML = "public.xml";
    String RESOURCE_IDX_XML = "idx.xml";
    String RUNTIME_DEX_FILENAME = "fasterc-runtime.dex";
    String DEPENDENCIES_FILENAME = "dependencies.json";
    String SOURCESET_SNAPSHOT_FILENAME = "sourceSets.json";
    String LAST_DIFF_RESULT_SET_FILENAME = "lastDiffResultSet.json";
    String ERROR_REPORT_FILENAME = "last-build-error-report.txt";
    String DEFAULT_LIBRARY_VARIANT_DIR_NAME = "release";
    String DEX_MERGE_JAR_FILENAME = "fasterc-dex-merge.jar";
    String STUDIO_INFO_SCRIPT_MACOS = "fasterc-studio-info-macos.sh";

    String FASTERC_BUILD_VERSION = "unspecified";

}