package top.gradle.fasterc.utils

/**
 * 自定义运行时异常
 */
class FasterCRuntimeException extends RuntimeException{

    FasterCRuntimeException() {
    }

    FasterCRuntimeException(String var1) {
        super(var1)
    }

    FasterCRuntimeException(String var1, Throwable var2) {
        super(var1, var2)
    }

    FasterCRuntimeException(Throwable var1) {
        super(var1)
    }

    FasterCRuntimeException(String var1, Throwable var2, boolean var3, boolean var4) {
        super(var1, var2, var3, var4)
    }
}