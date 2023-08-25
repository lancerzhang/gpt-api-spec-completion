package com.example.gasc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gpt-model")
public class GptModel {
    private String deployment;
    private String model;
    private String contextLength;
}
