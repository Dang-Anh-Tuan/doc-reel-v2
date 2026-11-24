package com.tuan.docreel.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/system/check} (SPEC mục 11): chi tiết self-check cho thẻ Hệ thống của web quản trị. */
@RestController
public class SystemCheckController {

    private final SystemCheckService service;

    public SystemCheckController(SystemCheckService service) {
        this.service = service;
    }

    @GetMapping("/api/system/check")
    public SystemCheckView check(@RequestParam(defaultValue = "false") boolean refresh) {
        return service.check(refresh);
    }
}
