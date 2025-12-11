package com.sashkomusic.mainagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "library")
public class LibraryConfig {

    private String rootPath;
}
