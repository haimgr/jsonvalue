plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.3.61"
    id("maven-publish")
}

group = "me.haimgr"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {

    targets {
        jvm()
        js()
        iosX64()
        iosArm64()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        named("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        named("jsMain") {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        named("jsTest") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

}
