package com.tuan.docreel.job;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Mọi lỗi API trả {@link ProblemDetail}; web hiển thị {@code title} + {@code detail}. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiFieldError(String field, String message) {}

    @ExceptionHandler(InvalidScriptException.class)
    ProblemDetail invalidScript(InvalidScriptException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Kịch bản không hợp lệ", e.getMessage());
        List<ApiFieldError> errors = e.errors().stream()
                .map(f -> new ApiFieldError(f.path(), f.message()))
                .toList();
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler(JobNotFoundException.class)
    ProblemDetail notFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy job", e.getMessage());
    }

    @ExceptionHandler(DuplicateJobException.class)
    ProblemDetail duplicate(DuplicateJobException e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Kịch bản đã được gửi", e.getMessage());
        p.setProperty("jobId", e.jobId());
        if (e.state() != null) {
            p.setProperty("state", e.state());
        }
        return p;
    }

    @ExceptionHandler(JobNotRetryableException.class)
    ProblemDetail notRetryable(JobNotRetryableException e) {
        return problem(HttpStatus.CONFLICT, "Không thể retry", e.getMessage());
    }

    @ExceptionHandler({IllegalStateTransitionException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception e) {
        return problem(HttpStatus.CONFLICT, "Job vừa bị thay đổi", "Job đang được xử lý ở nơi khác, thử lại sau");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Lỗi không lường trước", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống", "Có lỗi nội bộ, xem log của server");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setType(URI.create("about:blank"));
        return p;
    }
}
