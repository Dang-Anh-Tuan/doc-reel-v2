package com.tuan.docreel.video;

import java.nio.file.Path;
import java.util.List;

/** Ảnh + audio của một scene; {@code images} và {@code audios} cùng cỡ (quiz: 2 và 2). */
public record SceneMedia(String sceneId, List<Path> images, List<Path> audios) {

    public SceneMedia {
        images = List.copyOf(images);
        audios = List.copyOf(audios);
        if (images.isEmpty() || images.size() != audios.size()) {
            throw new IllegalArgumentException("Scene " + sceneId + ": số ảnh phải bằng số audio và > 0");
        }
    }
}
