package com.tuan.docreel.system;

import com.tuan.docreel.common.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Mục "Ổ đĩa": trống ở ổ chứa {@code work/} dưới 2 GB → WARN, dưới 500 MB → CRITICAL. */
@Component
@Order(40)
public class DiskSpaceCheck implements SelfCheckItem {

    static final String NAME = "Ổ đĩa";
    static final long WARN_BYTES = 2L << 30;
    static final long CRITICAL_BYTES = 500L << 20;
    private static final String FIX = "Dọn done/ và work/ cũ hoặc đổi DOCREEL_HOME sang ổ còn trống";

    private final Path work;
    private final ToLongFunction<Path> usableSpace;

    @Autowired
    public DiskSpaceCheck(AppProperties props) {
        this(props.dirs().work(), DiskSpaceCheck::usable);
    }

    DiskSpaceCheck(Path work, ToLongFunction<Path> usableSpace) {
        this.work = work;
        this.usableSpace = usableSpace;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        long free = usableSpace.applyAsLong(work);
        String detail = String.format("còn %.1f GB ở ổ chứa %s", free / (double) (1L << 30), work);
        if (free < CRITICAL_BYTES) {
            return CheckResult.critical(NAME, detail + " (< 500 MB)", FIX);
        }
        if (free < WARN_BYTES) {
            return CheckResult.warn(NAME, detail + " (< 2 GB)", FIX);
        }
        return CheckResult.ok(NAME, detail);
    }

    private static long usable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.getFileStore(dir).getUsableSpace();
        } catch (IOException e) {
            return 0;
        }
    }
}
