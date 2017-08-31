package top.gradle.fasterc.config;

/**
 * 全局的配置选项
 */

public class FasterCConfig {
    /**
     * 加速编译是否可用
     */
    boolean fasterCEnable = true
    /**
     * debug模式下打印的日志稍微多一些
     */
    boolean debug = true
    /**
     * 是否换成fasterComp的编译方式
     */
    boolean useCustomCompile = false
    /**
     * 每次都参与dex生成的class
     */
    String[] hotClasses = []
    /**
     * 当变化的java文件数量超过阈值,触发dex merge
     */
    int dexMergeThreshold = 4

    boolean handleReflectR = true
}