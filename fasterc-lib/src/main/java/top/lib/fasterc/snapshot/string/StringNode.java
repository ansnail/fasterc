package top.lib.fasterc.snapshot.string;


import top.lib.fasterc.snapshot.api.Node;

/**
 * Created by tong on 17/3/31.
 */
public class StringNode extends Node {
    public String string;

    public StringNode() {
    }

    public StringNode(String string) {
        this.string = string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    @Override
    public String getUniqueKey() {
        return string;
    }

    public static StringNode create(String string) {
         return new StringNode(string);
    }

    @Override
    public String toString() {
        return "StringNode{" +
                "string='" + string + '\'' +
                '}';
    }
}
