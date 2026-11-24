package com.tuan.docreel.job;

import com.tuan.docreel.script.FieldError;
import java.util.List;

/** Kịch bản gửi lên không qua được validator (trả 400 kèm danh sách lỗi theo trường). */
public class InvalidScriptException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<FieldError> errors;

    public InvalidScriptException(List<FieldError> errors) {
        super("Kịch bản không hợp lệ (" + errors.size() + " lỗi)");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
