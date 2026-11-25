package com.tuan.docreel.voice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quota TTS tháng này cho thẻ "ElevenLabs tháng này" ở trang Jobs. Luôn 200 (lỗi → {@code available: false}). */
@RestController
@RequestMapping("/api/tts")
public class TtsUsageController {

    private final TtsUsageService service;

    public TtsUsageController(TtsUsageService service) {
        this.service = service;
    }

    @GetMapping("/usage")
    public TtsUsageView usage() {
        return service.usage();
    }
}
