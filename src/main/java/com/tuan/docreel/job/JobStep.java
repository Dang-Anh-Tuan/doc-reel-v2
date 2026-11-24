package com.tuan.docreel.job;

/** Bước pipeline; hoàn thành một bước đưa job sang {@link #resultingState()}. */
public enum JobStep {
    RENDER(JobState.RENDERED),
    VOICE(JobState.VOICED),
    ASSEMBLE(JobState.ASSEMBLED),
    UPLOAD(JobState.UPLOADED);

    private final JobState resultingState;

    JobStep(JobState resultingState) {
        this.resultingState = resultingState;
    }

    public JobState resultingState() {
        return resultingState;
    }
}
