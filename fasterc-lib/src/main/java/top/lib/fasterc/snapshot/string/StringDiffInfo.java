package top.lib.fasterc.snapshot.string;


import top.lib.fasterc.snapshot.api.DiffInfo;
import top.lib.fasterc.snapshot.api.Status;

/**
 * Created by tong on 17/3/31.
 */
public class StringDiffInfo extends DiffInfo<StringNode> {
    public StringDiffInfo() {
    }

    public StringDiffInfo(Status status, StringNode now, StringNode old) {
        super(status, (now != null ? now.getUniqueKey() : old.getUniqueKey()), now, old);
    }
}
