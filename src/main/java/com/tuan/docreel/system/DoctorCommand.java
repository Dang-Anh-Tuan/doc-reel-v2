package com.tuan.docreel.system;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;

/**
 * {@code java -jar docreel.jar --doctor}: khởi động context tối thiểu (không web, không tự migrate), chạy
 * toàn bộ self-check, in bảng, thoát mã 0 (đạt) / 1 (có mục CRITICAL hoặc không khởi động được).
 *
 * <p>Đây là lớp duy nhất được ghi ra stdout ({@link #runAndExit}) — luật ArchUnit cấm System.out chừa riêng
 * lớp này vì in bảng cho người đọc là chức năng của lệnh CLI.
 */
public final class DoctorCommand {

    public static final String FLAG = "--doctor";

    private DoctorCommand() {}

    public static boolean isRequested(String[] args) {
        return Arrays.asList(args).contains(FLAG);
    }

    /** Điểm vào từ {@code main}: in ra stdout rồi {@code System.exit}. */
    public static void runAndExit(Class<?> applicationClass, String[] args) {
        int code = run(applicationClass, args, System.out);
        System.exit(code);
    }

    /** Chạy doctor, in bảng ra {@code out}, trả exit code. */
    public static int run(Class<?> applicationClass, String[] args, PrintStream out) {
        String[] springArgs = Arrays.stream(args).filter(a -> !FLAG.equals(a)).toArray(String[]::new);
        SpringApplication app = new SpringApplication(applicationClass);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.addListeners(new DoctorPropertiesListener());
        try (ConfigurableApplicationContext context = app.run(springArgs)) {
            SelfCheckReport report = context.getBean(SystemSelfCheck.class).runNow();
            out.print(DoctorReportFormatter.format(report));
            out.flush();
            return report.hasCritical() ? 1 : 0;
        } catch (RuntimeException e) {
            out.println("DocReel doctor: không khởi động được ứng dụng → KHÔNG ĐẠT");
            out.println("  " + rootMessage(e));
            out.flush();
            return 1;
        }
    }

    /**
     * Doctor chỉ đọc: không migrate, không validate schema, không đòi DB sống lúc khởi động (DB chết phải hiện
     * thành dòng CRITICAL trong bảng chứ không làm doctor sập).
     */
    static Map<String, Object> doctorProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.flyway.enabled", "false");
        props.put("spring.jpa.hibernate.ddl-auto", "none");
        props.put("spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access", "false");
        props.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("spring.datasource.hikari.initialization-fail-timeout", "-1");
        props.put("spring.datasource.hikari.connection-timeout", "5000");
        props.put("logging.level.root", "WARN");
        props.put("logging.level.com.tuan.docreel.system", "OFF");
        return props;
    }

    /**
     * Đặt {@link #doctorProperties()} lên đầu (ưu tiên cao nhất, đè cả application.yml) ngay sau khi file cấu
     * hình được nạp và trước khi hệ thống log khởi tạo (để mức log có hiệu lực).
     */
    static final class DoctorPropertiesListener
            implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

        static final String SOURCE_NAME = "docreelDoctor";

        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            event.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(SOURCE_NAME, doctorProperties()));
        }

        @Override
        public int getOrder() {
            // Sau EnvironmentPostProcessorApplicationListener (nạp application.yml), trước LoggingApplicationListener
            return EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER + 1;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
