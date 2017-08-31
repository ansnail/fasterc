package top.gradle.fasterc.transform

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform

/**
 * Transform基类，用于减少子类中共同的方法
 */

public class TransformProxy extends Transform {
    Transform base

    TransformProxy(Transform base) {
        this.base = base
    }

    @Override
    String getName() {
        return base.getName()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return base.getInputTypes()
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return base.getScopes()
    }

    @Override
    boolean isIncremental() {
        return base.isIncremental()
    }
}