package com.nexters.gamss

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class GamssApplication

fun main(args: Array<String>) {
    runApplication<GamssApplication>(*args)
}
