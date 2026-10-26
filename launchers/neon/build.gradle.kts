plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.launcheratlas.neon"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.launcheratlas.neon"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":core")) }
