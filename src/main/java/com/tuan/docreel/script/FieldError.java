package com.tuan.docreel.script;

/**
 * Một lỗi kịch bản gắn với đường dẫn trường.
 *
 * @param path vd {@code scenes[8].answer}; {@code $} nếu lỗi ở gốc (JSON hỏng)
 * @param message mô tả lỗi, đọc được cho người
 */
public record FieldError(String path, String message) {}
