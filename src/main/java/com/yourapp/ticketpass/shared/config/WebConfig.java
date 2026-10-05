package com.yourapp.ticketpass.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình Web MVC cho ứng dụng.
 * 
 * Định tuyến các yêu cầu tĩnh (như URL của file ảnh) tới đúng thư mục vật lý lưu trữ.
 * File này bị mất trong quá trình Git Reset và nay được khôi phục.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.local.base-dir}")
    private String baseDir;

    @Value("${app.storage.local.public-url-prefix}")
    private String publicUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Lấy đường dẫn tuyệt đối của thư mục lưu file
        Path uploadDir = Paths.get(baseDir);
        String uploadPath = uploadDir.toFile().getAbsolutePath();

        // Map URL prefix (vd: /files/**) tới thư mục vật lý (vd: file:C:/.../data/uploads/)
        String urlPattern = publicUrlPrefix + "/**";

        registry.addResourceHandler(urlPattern)
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
