package com.tuan.docreel.system;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SystemCheckControllerTest {

    private final SystemCheckService service = mock(SystemCheckService.class);
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new SystemCheckController(service)).build();

    private static SystemCheckView view() {
        return new SystemCheckView(
                CheckStatus.WARN,
                Instant.parse("2026-11-24T08:00:00Z"),
                List.of(CheckResult.ok("DB", "v1"), CheckResult.warn("TTS", "hết quota", "nạp thêm")));
    }

    @Test
    void returnsLastReportAsJson() throws Exception {
        when(service.check(false)).thenReturn(view());

        mvc.perform(get("/api/system/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARN"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].name").value("TTS"))
                .andExpect(jsonPath("$.items[1].status").value("WARN"))
                .andExpect(jsonPath("$.items[1].fix").value("nạp thêm"));
    }

    @Test
    void refreshParamRunsChecksNow() throws Exception {
        when(service.check(true)).thenReturn(view());

        mvc.perform(get("/api/system/check").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARN"));
    }
}
