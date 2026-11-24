package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ocr")
public class OcrConfig {

    private String engine;

    private String tessdataPath;

    private String language;

    private int minConfidence;

    private boolean enabled;
}
