package com.tuan.docreel.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback cho web quản trị (SPEC 7.1): route con của React ({@code /admin/jobs/123}) không phải file → trả
 * {@code /admin/index.html}, để mở thẳng link / F5 không 404.
 *
 * <p>Chỉ khớp đường dẫn có đoạn cuối <b>không chứa dấu chấm</b>, nên file tĩnh ({@code /admin/assets/x.js},
 * {@code /admin/index.html}) vẫn do resource handler của Spring phục vụ; file không tồn tại → 404 (ProblemDetail),
 * không bao giờ trả HTML thay cho JS/CSS. Hỗ trợ route sâu tới 4 cấp (đủ cho {@code /videos/:id/...}).
 */
@Controller
public class AdminSpaController {

    static final String INDEX = "/admin/index.html";

    private static final String LAST = ":[^.]+}";

    @GetMapping({
        "/admin",
        "/admin/",
        "/admin/{a" + LAST,
        "/admin/{a}/{b" + LAST,
        "/admin/{a}/{b}/{c" + LAST,
        "/admin/{a}/{b}/{c}/{d" + LAST
    })
    public String index() {
        return "forward:" + INDEX;
    }
}
