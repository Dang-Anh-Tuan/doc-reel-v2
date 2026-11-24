package com.tuan.docreel.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.script.VideoType;
import java.util.List;
import org.junit.jupiter.api.Test;

class YouTubeMetadataTest {

    static VideoScript script(String title, List<String> sources) {
        return new VideoScript("R-08", "React", title, 1, VideoType.SLIDES, null, sources, List.of(), null);
    }

    @Test
    void titleOver100CharsIsTruncatedTo100() {
        YouTubeMetadata m = YouTubeMetadata.from(script("á".repeat(150), List.of()));
        assertThat(m.title().codePointCount(0, m.title().length())).isEqualTo(100);
    }

    @Test
    void shortTitleIsKept() {
        assertThat(YouTubeMetadata.from(script("Rules of Hooks", List.of())).title())
                .isEqualTo("Rules of Hooks");
    }

    @Test
    void descriptionContainsSources() {
        YouTubeMetadata m = YouTubeMetadata.from(script("T", List.of("https://react.dev/a", "https://react.dev/b")));
        assertThat(m.description()).contains("https://react.dev/a", "https://react.dev/b");
    }

    @Test
    void tagsFollowTrackAndPrivacyIsPrivate() {
        YouTubeMetadata m = YouTubeMetadata.from(script("T", null));
        assertThat(m.tags()).contains("React", "R-08");
        assertThat(m.privacyStatus()).isEqualTo("private");
    }
}
