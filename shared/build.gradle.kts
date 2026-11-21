plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // The JVM target runs the unit tests on any machine.
    // The Android and iOS targets are added with the app modules.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
