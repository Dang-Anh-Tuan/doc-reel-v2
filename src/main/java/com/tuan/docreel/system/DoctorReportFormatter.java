package com.tuan.docreel.system;

import java.util.List;

/** In {@link SelfCheckReport} thành bảng chữ cho {@code --doctor}. */
public final class DoctorReportFormatter {

    private static final String[] HEADER = {"Mục", "Kết quả", "Chi tiết"};

    private DoctorReportFormatter() {}

    public static String format(SelfCheckReport report) {
        List<String[]> rows = report.results().stream()
                .map(r -> new String[] {r.name(), r.status().name(), r.detail()})
                .toList();
        int[] widths = new int[HEADER.length];
        for (int i = 0; i < HEADER.length; i++) {
            widths[i] = HEADER[i].length();
            for (String[] row : rows) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("DocReel doctor - ").append(report.checkedAt()).append(System.lineSeparator());
        String separator = separator(widths);
        out.append(separator);
        appendRow(out, HEADER, widths);
        out.append(separator);
        rows.forEach(row -> appendRow(out, row, widths));
        out.append(separator);

        report.results().stream()
                .filter(r -> r.status() != CheckStatus.OK && r.fix() != null)
                .forEach(r -> out.append("→ ")
                        .append(r.name())
                        .append(": ")
                        .append(r.fix())
                        .append(System.lineSeparator()));
        out.append(report.hasCritical() ? "KẾT LUẬN: KHÔNG ĐẠT (có mục CRITICAL)" : "KẾT LUẬN: ĐẠT")
                .append(System.lineSeparator());
        return out.toString();
    }

    private static String separator(int[] widths) {
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            line.append("-".repeat(width + 2)).append('+');
        }
        return line.append(System.lineSeparator()).toString();
    }

    private static void appendRow(StringBuilder out, String[] cells, int[] widths) {
        out.append('|');
        for (int i = 0; i < cells.length; i++) {
            out.append(' ')
                    .append(cells[i])
                    .append(" ".repeat(widths[i] - cells[i].length()))
                    .append(" |");
        }
        out.append(System.lineSeparator());
    }
}
