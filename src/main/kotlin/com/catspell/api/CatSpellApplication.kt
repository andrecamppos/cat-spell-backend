package com.catspell.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CatSpellApplication

fun main(args: Array<String>) {
    runApplication<CatSpellApplication>(*args)
}
