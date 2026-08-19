plugins {
    id("com.android.application")
}

// Release signing: CI provides the keystore via environment variables
// (REDAHM_KEYSTORE_FILE/REDAHM_KEYSTORE_PASSWORD/REDAHM_KEY_ALIAS/
// REDAHM_KEY_PASSWORD, sourced from GitHub Actions secrets). Local builds
// without them keep the default (debug) signing config, so assembleRelease
// still produces an installable APK for quick tests.
val releaseKeystoreFile = System.getenv("REDAHM_KEYSTORE_FILE")

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

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("REDAHM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("REDAHM_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("REDAHM_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // directory. Relative to the app module dir (android/app): two levels up
    // reaches the repo root, then down into native/build-android/lib/<abi>/.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("../../native/build-android/lib")
        }
    }
}