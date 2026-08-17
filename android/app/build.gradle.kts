plugins {
    id("com.android.application")
}

android {
    namespace = "io.redahm.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.redahm.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packagingOptions {
        // Keep the (large) native libraries compressed inside the APK and
        // extract them at install time (equivalent to the manifest attribute
        // extractNativeLibs="true", which AGP 9 rejects in the manifest).
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Native libraries (libmain.so, libSDL3.so, librexruntime.so,
    // librexgpu-xenos.so) are produced by the build-android.sh script (run by
    // CI before Gradle) and consumed from the shared native build output
    // directory.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("../../../native/build-android/lib")
        }
    }
}