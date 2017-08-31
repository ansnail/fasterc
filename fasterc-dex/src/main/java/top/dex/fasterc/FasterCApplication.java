package top.dex.fasterc;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Field;
import java.util.Set;

import top.dex.fasterc.multidex.MultiDex;

/**
 * Created by yanjie on 2017-08-17.
 * Describe:代理Application类，在运行时，替换为真正的Application
 */

public class FasterCApplication extends Application {
    private Context context;
    //项目的真正Application，用于运行过程中
    private Application realApplication;


    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        MultiDex.install(context);
        fixGoogleMultiDex(context);
    }















    private void fixGoogleMultiDex(Context context) {
        try {
            Class clazz = getClassLoader().loadClass("android.support.multidex.MultiDex");
            Field field = clazz.getDeclaredField("installedApk");
            field.setAccessible(true);
            Set<String> installedApk = (Set<String>) field.get(null);
            installedApk.addAll(MultiDex.installedApk);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}
