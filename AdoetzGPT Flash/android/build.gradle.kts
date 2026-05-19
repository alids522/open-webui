// Top-level build file for AdoetzGPT Flash
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
