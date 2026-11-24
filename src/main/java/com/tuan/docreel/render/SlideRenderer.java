package com.tuan.docreel.render;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Scene → HTML bằng Thymeleaf (SPEC 7). Model: {@code video}, {@code scene}, {@code index}, {@code total},
 * {@code frame}. Không cần web context: link {@code @{/css/..}} giữ nguyên dạng gốc.
 */
@Component
public class SlideRenderer {

    public static final String DEFAULT_THEME = "midnight-brass";

    /** 10 theme trong static/css/themes/ (SPEC 7). */
    public static final Set<String> THEMES = Set.of(
            "midnight-brass",
            "petrol-sage",
            "indigo-dust",
            "navy-terracotta",
            "slate-mustard",
            "deep-ocean-coral",
            "blueprint-cream",
            "storm-rose",
            "teal-navy-copper",
            "prussian-olive");

    private static final Map<Class<?>, String> TYPE_NAMES = Arrays.stream(
                    Scene.class.getAnnotation(JsonSubTypes.class).value())
            .collect(Collectors.toMap(JsonSubTypes.Type::value, JsonSubTypes.Type::name));

    private final SpringTemplateEngine engine;

    public SlideRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(
                    org.thymeleaf.context.IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
        });
    }

    /** Tên {@code type} trong JSON của scene. */
    public static String typeOf(Scene scene) {
        String name = TYPE_NAMES.get(scene.getClass());
        if (name == null) {
            throw new IllegalArgumentException(
                    "Không có template cho " + scene.getClass().getSimpleName());
        }
        return name;
    }

    /** @param frame chỉ dùng cho {@code quiz} (1 hoặc 2); null → 1 */
    public Map<String, Object> model(VideoScript video, Scene scene, int index, int total, Integer frame) {
        String theme = video.theme() == null ? DEFAULT_THEME : video.theme();
        if (!THEMES.contains(theme)) {
            throw new UnknownThemeException(theme);
        }
        Map<String, Object> videoModel = new LinkedHashMap<>();
        videoModel.put("topicId", video.topicId());
        videoModel.put("track", video.track());
        videoModel.put("title", video.title());
        videoModel.put("theme", theme);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("video", videoModel);
        model.put("scene", scene);
        model.put("index", index);
        model.put("total", total);
        model.put("frame", frame == null ? 1 : frame);
        return model;
    }

    public String render(VideoScript video, Scene scene, int index, int total, Integer frame) {
        Map<String, Object> model = model(video, scene, index, total, frame);
        Context context = new Context(java.util.Locale.forLanguageTag("vi"), model);
        return engine.process(new TemplateSpec("scenes/" + typeOf(scene), TemplateMode.HTML), context);
    }
}
