package com.tuan.docreel.notify;

/** Nơi nhận thông báo đổi state (log bây giờ; Telegram ở bản 2). */
public interface JobNotifier {

    void onStateChanged(JobStateChangedEvent event);
}
