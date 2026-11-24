package com.tuan.docreel.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * HTML slide → PNG 1080×1920 bằng Playwright/Chromium. Tài nguyên tĩnh ({@code /css}, {@code /js},
 * {@code /vendor}) lấy thẳng từ classpath {@code static/} qua route chặn request, không cần mở server.
 */
@Component
public class SlideCapturer implements AutoCloseable {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;

    private static final Logger log = LoggerFactory.getLogger(SlideCapturer.class);
    private static final String ORIGIN = "http://docreel.local";
    private static final double READY_TIMEOUT_MS = 15_000;

    /** Kết quả chụp: 1 PNG mỗi frame + danh sách lỗi tràn (rỗng = ổn). */
    public record Capture(List<byte[]> frames, List<String> problems) {

        public boolean hasProblems() {
            return !problems.isEmpty();
        }
    }

    private Playwright playwright;
    private Browser browser;

    /** Chụp, không ném lỗi tràn — cho gallery/preview. */
    public synchronized Capture inspect(String html) {
        Browser b = browser();
        try (BrowserContext context = b.newContext(
                new Browser.NewContextOptions().setViewportSize(WIDTH, HEIGHT).setDeviceScaleFactor(1))) {
            Page page = context.newPage();
            page.route(ORIGIN + "/**", route -> serve(route, html));
            page.navigate(ORIGIN + "/slide");
            page.waitForSelector(
                    "body[data-ready=\"1\"]",
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(READY_TIMEOUT_MS));
            List<String> problems =
                    SlideOverflowException.parseProblems((String) page.evaluate("document.body.dataset.problems"));
            int frames = Integer.parseInt(String.valueOf(page.evaluate("document.body.dataset.frames || '1'")));
            List<byte[]> shots = new ArrayList<>();
            for (int f = 1; f <= frames; f++) {
                if (f > 1) {
                    page.evaluate("f => document.body.setAttribute('data-frame', String(f))", f);
                }
                shots.add(page.screenshot(new Page.ScreenshotOptions().setFullPage(false)));
            }
            return new Capture(shots, problems);
        }
    }

    /** Chụp và ném {@link SlideOverflowException} nếu slide tràn (không retry). */
    public Capture capture(String sceneId, String html) {
        Capture result = inspect(html);
        if (result.hasProblems()) {
            log.warn("Slide {} tràn: {}", sceneId, result.problems());
            throw new SlideOverflowException(sceneId, result.problems());
        }
        return result;
    }

    private static void serve(Route route, String html) {
        String path = URI.create(route.request().url()).getPath();
        if ("/slide".equals(path)) {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("text/html; charset=utf-8")
                    .setBodyBytes(html.getBytes(StandardCharsets.UTF_8)));
            return;
        }
        ClassPathResource resource = new ClassPathResource("static" + path);
        if (path.contains("..") || !resource.exists()) {
            route.fulfill(new Route.FulfillOptions().setStatus(404));
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setHeaders(Map.of("Access-Control-Allow-Origin", "*"))
                    .setContentType(contentType(path))
                    .setBodyBytes(in.readAllBytes()));
        } catch (IOException e) {
            route.fulfill(new Route.FulfillOptions().setStatus(500));
        }
    }

    static String contentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private Browser browser() {
        if (browser == null || !browser.isConnected()) {
            if (playwright == null) {
                // Không tự tải trình duyệt lúc chạy: Chromium cài sẵn (playwright install chromium), tránh tải
                // firefox/webkit.
                playwright = Playwright.create(
                        new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            }
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            log.info("Đã mở Chromium headless cho SlideCapturer");
        }
        return browser;
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
