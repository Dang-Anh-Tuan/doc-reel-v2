package com.tuan.docreel.script;

/** Scene có tiêu đề chung: {@code accent} (trong pill) + {@code titleRest}. */
public sealed interface TitledScene extends Scene
        permits TitleScene,
                PointsScene,
                CodeScene,
                CodeCompareScene,
                CompareScene,
                StepsScene,
                TableScene,
                SummaryScene {

    String accent();

    String titleRest();
}
