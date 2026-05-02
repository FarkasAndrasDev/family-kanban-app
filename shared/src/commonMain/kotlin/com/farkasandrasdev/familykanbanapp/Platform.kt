package com.farkasandrasdev.familykanbanapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform