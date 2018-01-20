package com.aurea.testgenerator.config

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import java.nio.file.Path


@Configuration
@ConfigurationProperties
@EnableConfigurationProperties
@Canonical
@Validated
class PipelineProperties {
    @NotEmpty
    String src
    @NotEmpty
    String testSrc
    @NotNull
    Path out
}
