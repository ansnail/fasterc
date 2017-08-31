package top.lib.fasterc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 序列化工具，将json字符串转化为对象或将对象存储起来
 */
public class SerializeUtils {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 从json串中读取对象
     */
    public static <T> T load(InputStream inputStream,Class<T> type) throws IOException {
        String json = new String(FileUtils.readStream(inputStream));
        return GSON.fromJson(json,type);
    }

    /**
     * 将对象序列化到本地
     */
    public static void serializeTo(OutputStream outputStream,Object obj) throws IOException {
        String json = GSON.toJson(obj);
        try {
            outputStream.write(json.getBytes());
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    /**
     * 将对象序列化到本地
     */
    public static void serializeTo(File file,Object obj) throws IOException {
        String json = GSON.toJson(obj);
        FileOutputStream outputStream = null;
        FileUtils.ensureDir(file.getParentFile());
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(json.getBytes());
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    /**
     * 对象转换为字符串
     */
    public static String loadSnapshotString(Object obj){
        return GSON.toJson(obj);
    }
}
