package com.yourapp.ticketpass.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.local.base-dir}")
    private String baseDir;

    @Value("${app.storage.local.public-url-prefix}")
    private String publicUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(baseDir);
        String uploadPath = uploadDir.toFile().getAbsolutePath();

        String urlPattern = publicUrlPrefix + "/**";

        registry.addResourceHandler(urlPattern)
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
