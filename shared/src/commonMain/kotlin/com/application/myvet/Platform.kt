package com.application.myvet

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform