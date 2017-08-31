package top.lib.fasterc.utils;

/**
 * Created by yanjie on 2017-08-17.
 * Describe: 字符串工具类
 */

public class TextUtils {
    /**
     * 判断字符串是否为空
     * @param str 字符串
     * @return 是否为空
     */
    public static boolean isEmpty(String str) {
        return (str == null || str.trim().length() == 0);
    }
}
