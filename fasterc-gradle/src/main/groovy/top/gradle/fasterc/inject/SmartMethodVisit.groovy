package top.gradle.fasterc.inject

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor

import static org.objectweb.asm.Opcodes.*

class SmartMethodVisit extends MethodVisitor {

    public SmartMethodVisit(MethodVisitor mv) {
        super(ASM5, mv);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        super.visitMethodInsn(opcode, owner, name, desc, false);
    }

    @Override
    public void visitCode() {
        // 此方法在访问方法的头部时被访问到，仅被访问一次
        // 此处可插入新的指令
        //System.out.println("Hello World!");
        super.visitCode();
    }


    @Override
    public void visitInsn(int opcode) {
        // 此方法可以获取方法中每一条指令的操作类型，被访问多次
        // 如应在方法结尾处添加新指令，则应判断：
        super.visitInsn(opcode);
//        if (opcode == RETURN) {
//            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//            mv.visitFieldInsn(GETSTATIC, "top.dex.fasterc.antilazyload.AntiLazyLoad", "codeStr", "Ljava/lang/String;");
//            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
//        }

        if (opcode == RETURN) {
            super.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
            super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            Label l0 = new Label();
            super.visitJumpInsn(IFEQ, l0);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitFieldInsn(GETSTATIC, "top.dex.fasterc.antilazyload.AntiLazyLoad", "codeStr", "Ljava/lang/String;");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            super.visitLabel(l0);
        }

    }

}