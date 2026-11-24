package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DevPreviewControllerTest {

    private final DevPreviewController controller = new DevPreviewController(new SlideRenderer(), Path.of("samples"));

    @Test
    void rendersSampleHtml() throws Exception {
        var res = controller.preview("quiz", "ok", 2, "petrol-sage");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("data-frame=\"2\"").contains("petrol-sage");
    }

    @Test
    void rejectsPathTraversalAndUnknownVariant() throws Exception {
        assertThat(controller.preview("../x", "ok", 1, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.preview("quiz", "zzz", 1, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownTypeIs404() throws Exception {
        assertThat(controller.preview("nope", "ok", 1, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
