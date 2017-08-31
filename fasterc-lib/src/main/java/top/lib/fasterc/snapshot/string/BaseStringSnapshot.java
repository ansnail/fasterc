package top.lib.fasterc.snapshot.string;

import java.io.IOException;
import java.util.Set;

import top.lib.fasterc.snapshot.api.DiffInfo;
import top.lib.fasterc.snapshot.api.DiffResultSet;
import top.lib.fasterc.snapshot.api.Snapshot;
import top.lib.fasterc.snapshot.api.Status;


/**
 * Created by tong on 17/3/31.
 */
public class BaseStringSnapshot<DIFF_INFO extends StringDiffInfo,NODE extends StringNode> extends Snapshot<DIFF_INFO,NODE> {

    public BaseStringSnapshot() {
    }

    public BaseStringSnapshot(BaseStringSnapshot snapshoot) {
        super(snapshoot);
    }

    public BaseStringSnapshot(Set<String> strings) throws IOException {
        for (String str : strings) {
            addNode((NODE) StringNode.create(str));
        }
    }

    public BaseStringSnapshot(String ...strings) throws IOException {
        for (String str : strings) {
            addNode((NODE) StringNode.create(str));
        }
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new StringDiffInfo();
    }

    @Override
    protected void diffNode(DiffResultSet<DIFF_INFO> diffInfos, Snapshot<DIFF_INFO, NODE> otherSnapshoot, NODE now, NODE old) {
        //不需要对比变化
        addDiffInfo(diffInfos,createDiffInfo(Status.NOCHANGED,now,old));
    }
}
