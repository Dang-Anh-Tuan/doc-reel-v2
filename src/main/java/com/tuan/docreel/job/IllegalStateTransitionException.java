package com.tuan.docreel.job;

/** Chuyển trạng thái job không nằm trong máy trạng thái (SPEC mục 5). */
public class IllegalStateTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final JobState from;
    private final JobState to;

    public IllegalStateTransitionException(JobState from, JobState to) {
        super("Không thể chuyển job từ " + from + " sang " + to);
        this.from = from;
        this.to = to;
    }

    public JobState from() {
        return from;
    }

    public JobState to() {
        return to;
    }
}
