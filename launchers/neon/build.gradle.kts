plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.launcheratlas.neon"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.launcheratlas.neon"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":core")) }
