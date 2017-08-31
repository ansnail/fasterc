package top.lib.fasterc.snapshot.file;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/29.
 */
public class FileSuffixFilter implements ScanFilter {
    private final Set<String> suffixList = new HashSet<>();
    
    public FileSuffixFilter(String suffix) {
        addSuffix(suffix);
    }

    public FileSuffixFilter(Set<String> suffixList) {
        for (String suffix : suffixList) {
            addSuffix(suffix);
        }
    }

    public void addSuffix(String suffix) {
        if (suffix == null || suffix.length() == 0) {
            throw new IllegalArgumentException("suffix can not be epmty!!");
        }
        this.suffixList.add(suffix);

        if (this.suffixList.isEmpty()) {
            throw new IllegalArgumentException("suffix list can not be epmty!!");
        }
    }

    public Set<String> getSuffixList() {
        return suffixList;
    }

    @Override
    public boolean preVisitFile(File file) {
        for (String suffix : suffixList) {
            if (file.getName().endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
