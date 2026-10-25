plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.launcheratlas.paper"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.launcheratlas.paper"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":core")) }
