package com.application.myvet.data.network

actual object ApiConfig {
    // Android emulator reaches XAMPP running on the development computer through 10.0.2.2.
    actual val baseUrl = "http://10.0.2.2/public/api"
}
