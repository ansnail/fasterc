package top.lib.fasterc.snapshot.string;

import java.io.IOException;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public final class StringSnapshot extends BaseStringSnapshot<StringDiffInfo,StringNode> {
    public StringSnapshot() {
    }

    public StringSnapshot(StringSnapshot snapshoot) {
        super(snapshoot);
    }

    public StringSnapshot(Set<String> strings) throws IOException {
        super(strings);
    }

    public StringSnapshot(String... strings) throws IOException {
        super(strings);
    }
}
