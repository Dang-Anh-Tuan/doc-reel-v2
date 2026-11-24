package com.tuan.docreel.script;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Một slide. Trường {@code type} trong JSON chọn record cụ thể (SPEC 9.1, mục 7).
 *
 * <p>Trường chung: {@code id}, {@code narration}, {@code spoken} (thay narration khi đọc). Type có tiêu đề
 * chung thì có thêm {@code accent} + {@code titleRest} (xem {@link TitledScene}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TitleScene.class, name = "title"),
    @JsonSubTypes.Type(value = PointsScene.class, name = "points"),
    @JsonSubTypes.Type(value = CodeScene.class, name = "code"),
    @JsonSubTypes.Type(value = CodeCompareScene.class, name = "codeCompare"),
    @JsonSubTypes.Type(value = CompareScene.class, name = "compare"),
    @JsonSubTypes.Type(value = DefinitionScene.class, name = "definition"),
    @JsonSubTypes.Type(value = StepsScene.class, name = "steps"),
    @JsonSubTypes.Type(value = TableScene.class, name = "table"),
    @JsonSubTypes.Type(value = QuizScene.class, name = "quiz"),
    @JsonSubTypes.Type(value = SummaryScene.class, name = "summary")
})
public sealed interface Scene permits TitledScene, DefinitionScene, QuizScene {

    String id();

    String narration();

    String spoken();
}
