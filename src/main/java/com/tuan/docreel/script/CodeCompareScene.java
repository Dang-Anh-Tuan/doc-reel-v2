package com.tuan.docreel.script;

/** {@code codeCompare}: code sai ({@code bad}) trên, code đúng ({@code good}) dưới. */
public record CodeCompareScene(
        String id,
        String narration,
        String spoken,
        String accent,
        String titleRest,
        String lang,
        CodeBlock bad,
        CodeBlock good,
        String note)
        implements TitledScene {

    public record CodeBlock(String label, String code) {}
}
