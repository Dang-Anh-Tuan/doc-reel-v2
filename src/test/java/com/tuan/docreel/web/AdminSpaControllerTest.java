package com.tuan.docreel.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminSpaControllerTest {

    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new AdminSpaController()).build();

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/", "/admin/jobs", "/admin/jobs/123", "/admin/videos/abc/comments"})
    void forwardsClientRoutesToIndexHtml(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl(AdminSpaController.INDEX));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/assets/x.js", "/admin/favicon.svg", "/admin/index.html"})
    void doesNotHandlePathsThatLookLikeFiles(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }
}
