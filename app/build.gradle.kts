plugins {
    id("com.android.application")
}

val signingStorePath = providers.gradleProperty("ANKIMATCH_KEYSTORE_PATH")
    .orElse(providers.environmentVariable("ANKIMATCH_KEYSTORE_PATH"))
    .orNull
val signingStorePassword = providers.gradleProperty("ANKIMATCH_KEYSTORE_PASSWORD")
    .orElse(providers.environmentVariable("ANKIMATCH_KEYSTORE_PASSWORD"))
    .orNull
val signingKeyAlias = providers.gradleProperty("ANKIMATCH_KEY_ALIAS")
    .orElse(providers.environmentVariable("ANKIMATCH_KEY_ALIAS"))
    .orNull
val signingKeyPassword = providers.gradleProperty("ANKIMATCH_KEY_PASSWORD")
    .orElse(providers.environmentVariable("ANKIMATCH_KEY_PASSWORD"))
    .orNull
val hasPrivateSigningConfig = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "de.gabriel.ankimatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.gabriel.ankimatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.0"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        if (hasPrivateSigningConfig) {
            create("ankiMatchPrivate") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("ankiMatchPrivate")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
