package com.tuan.docreel.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.tuan.docreel.system.CheckResult;
import com.tuan.docreel.system.SelfCheckItem;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mục "Chromium" (CRITICAL): mở Chromium headless và nạp trang trống. Dùng Playwright riêng (không đụng
 * {@link SlideCapturer}); đặt trong {@code render} vì {@code system} không được phụ thuộc feature khác.
 */
@Component
@Order(35)
public class ChromiumCheck implements SelfCheckItem {

    static final String NAME = "Chromium";
    private static final String FIX =
            "Cài Chromium cho Playwright: `npx playwright install chromium` (đúng bản Playwright của dự án)";

    private final Supplier<String> opener;

    @Autowired
    public ChromiumCheck() {
        this(ChromiumCheck::openBlankPage);
    }

    ChromiumCheck(Supplier<String> opener) {
        this.opener = opener;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        try {
            return CheckResult.ok(NAME, "mở được trang trống (Chromium " + opener.get() + ")");
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String oneLine = msg.replaceAll("\\s+", " ").trim();
            if (oneLine.length() > 300) {
                oneLine = oneLine.substring(0, 300) + "...";
            }
            return CheckResult.critical(NAME, "không mở được Chromium: " + oneLine, FIX);
        }
    }

    private static String openBlankPage() {
        try (Playwright playwright = Playwright.create(
                new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                browser.newPage().navigate("about:blank");
                return browser.version();
            }
        }
    }
}
