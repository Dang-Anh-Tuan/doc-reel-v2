package com.tuan.docreel.youtube;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * {@code java -jar docreel.jar --youtube-auth}: cấp quyền YouTube lần đầu. Mở cổng loopback, ghi URL đồng ý ra
 * log, nhận {@code code} từ Google rồi đổi lấy refresh token (lưu mã hóa qua {@link OAuthTokenStore}).
 */
@Component
public class YouTubeAuthRunner implements ApplicationRunner {

    public static final String FLAG = "youtube-auth";
    private static final String SCOPE =
            "https://www.googleapis.com/auth/youtube.upload " + "https://www.googleapis.com/auth/youtube";

    private static final Logger log = LoggerFactory.getLogger(YouTubeAuthRunner.class);

    private final YouTubeProperties props;
    private final AccessTokenProvider tokens;

    public YouTubeAuthRunner(YouTubeProperties props, AccessTokenProvider tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption(FLAG)) {
            return;
        }
        CompletableFuture<String> code = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String found = null;
            if (query != null) {
                for (String kv : query.split("&")) {
                    if (kv.startsWith("code=")) {
                        found = URLDecoder.decode(kv.substring(5), StandardCharsets.UTF_8);
                    }
                }
            }
            byte[] body = (found == null ? "Thiếu code" : "Đã cấp quyền, có thể đóng tab này.")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(found == null ? 400 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            if (found != null) {
                code.complete(found);
            }
        });
        server.start();
        try {
            String redirect = "http://127.0.0.1:" + server.getAddress().getPort();
            log.info("Mở URL sau trong trình duyệt để cấp quyền YouTube: {}", authUrl(redirect));
            tokens.exchangeCode(code.get(5, TimeUnit.MINUTES), redirect);
            log.info("Đã lưu refresh token (mã hóa) vào {}", props.tokenFile());
        } finally {
            server.stop(0);
        }
    }

    String authUrl(String redirect) {
        return props.authUrl() + "?response_type=code&access_type=offline&prompt=consent"
                + "&client_id=" + enc(props.clientId() == null ? "" : props.clientId())
                + "&redirect_uri=" + enc(redirect)
                + "&scope=" + enc(SCOPE);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
