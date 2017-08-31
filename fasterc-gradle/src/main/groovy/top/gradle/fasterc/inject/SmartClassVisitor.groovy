package top.gradle.fasterc.inject

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

import static org.objectweb.asm.Opcodes.ASM5;

class SmartClassVisitor extends ClassVisitor {
    public SmartClassVisitor(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ("<init>" == name) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);// 先得到原始的方法
            return new SmartMethodVisit(mv);
        } else {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }
}