package com.tuan.docreel.job;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tìm PNG đã render của job trong {@code work/{jobId}/}. Tên file theo đúng quy ước của bước render
 * ({@code {sceneId}.png}, quiz thêm {@code {sceneId}-reveal.png}) — viết lại ở đây để {@code job} không phụ thuộc
 * {@code render} (render đã phụ thuộc job → vòng). {@code JobSlidesTest} khóa hai bên khớp nhau.
 *
 * <p>An toàn đường dẫn: chỉ ghép từ {@code jobId} (UUID đã parse) + tên lấy từ kịch bản theo số thứ tự nguyên; sau
 * khi normalize phải nằm ngay trong thư mục của job, là file thường (không theo symlink).
 */
@Component
public class JobSlides {

    static final String REVEAL_SUFFIX = "-reveal";
    private static final String PNG = ".png";
    /** 1..9999, không số 0 đầu, không dấu. */
    private static final Pattern INDEX = Pattern.compile("[1-9]\\d{0,3}");

    private final Path workRoot;
    private final JsonMapper mapper;

    public JobSlides(AppProperties props, JsonMapper mapper) {
        this.workRoot = props.dirs().work().toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    /** Kịch bản đã lưu của job; JSON hỏng / thiếu → rỗng. */
    Optional<VideoScript> script(VideoJob job) {
        if (job.getScript() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readValue(job.getScript(), VideoScript.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    /** Tên file PNG theo thứ tự timeline (giống {@code RenderStep.imageNames}). */
    static List<String> imageNames(VideoScript script) {
        List<String> names = new ArrayList<>();
        if (script == null || script.scenes() == null) {
            return names;
        }
        for (Scene scene : script.scenes()) {
            names.add(scene.id() + PNG);
            if (scene instanceof QuizScene) {
                names.add(scene.id() + REVEAL_SUFFIX + PNG);
            }
        }
        return names;
    }

    /** Các slide đã có file trên đĩa, giữ số thứ tự gốc (slide chưa render thì không có trong danh sách). */
    List<JobDetail.Slide> rendered(UUID jobId, VideoScript script) {
        List<String> names = imageNames(script);
        List<JobDetail.Slide> slides = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (resolve(jobId, name).isPresent()) {
                int index = i + 1;
                slides.add(new JobDetail.Slide(
                        index,
                        name.substring(0, name.length() - PNG.length()),
                        "/api/jobs/" + jobId + "/slides/" + index + PNG));
            }
        }
        return slides;
    }

    /** File PNG thứ {@code n} (chuỗi lấy từ URL) của job; mọi trường hợp không hợp lệ → rỗng. */
    Optional<Path> find(VideoJob job, String n) {
        if (n == null || !INDEX.matcher(n).matches()) {
            return Optional.empty();
        }
        int index = Integer.parseInt(n);
        List<String> names = imageNames(script(job).orElse(null));
        if (index > names.size()) {
            return Optional.empty();
        }
        return resolve(job.getId(), names.get(index - 1));
    }

    private Optional<Path> resolve(UUID jobId, String name) {
        Path dir = workRoot.resolve(jobId.toString()).normalize();
        Path file = dir.resolve(name).normalize();
        if (!dir.equals(file.getParent()) || !file.getFileName().toString().endsWith(PNG)) {
            return Optional.empty();
        }
        try {
            boolean ok = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 0;
            return ok ? Optional.of(file) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
