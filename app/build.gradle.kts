import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}
val signingNames = listOf("LANU_KEYSTORE", "LANU_STORE_PASSWORD", "LANU_KEY_ALIAS", "LANU_KEY_PASSWORD", "LANU_CERT_SHA256")
val signingValues = signingNames.associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val hasProductionSigning = signingValues.values.all { it != null }
check(signingValues.values.none { it != null } || hasProductionSigning) {
    "Üretim imzası eksik yapılandırılmış. Beş LANU imza değişkeninin tamamı gerekli."
}
if (providers.gradleProperty("lanu.requireProductionSigning").orNull == "true") {
    check(hasProductionSigning) { "Kalıcı üretim imzası gerekli; debug imzasıyla yayın yapılmayacak." }
}
val verifyProductionSigning = tasks.register("verifyProductionSigning") {
    doLast {
        check(hasProductionSigning) { "Kalıcı üretim imzası yapılandırılmadı." }
        val expected = signingValues.getValue("LANU_CERT_SHA256")!!.replace(":", "").lowercase()
        check(expected.matches(Regex("[a-f0-9]{64}"))) { "Üretim sertifika SHA-256 biçimi geçersiz." }
        val storePassword = signingValues.getValue("LANU_STORE_PASSWORD")!!.toCharArray()
        val keyPassword = signingValues.getValue("LANU_KEY_PASSWORD")!!.toCharArray()
        try {
            val store = KeyStore.getInstance("JKS")
            file(signingValues.getValue("LANU_KEYSTORE")!!).inputStream().use { store.load(it, storePassword) }
            val alias = signingValues.getValue("LANU_KEY_ALIAS")!!
            check(store.isKeyEntry(alias) && store.getKey(alias, keyPassword) is java.security.PrivateKey) { "Üretim imza anahtarı okunamadı." }
            val cert = store.getCertificate(alias) ?: error("Üretim sertifikası bulunamadı.")
            val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
            check(actual == expected) { "Üretim sertifikası beklenen SHA-256 ile eşleşmiyor; yayın durduruldu." }
        } finally { storePassword.fill('\u0000'); keyPassword.fill('\u0000') }
    }
}
val uiSmoke = providers.gradleProperty("lanu.uiSmoke").orNull == "true"
val firebaseEnabled = !uiSmoke && file("google-services.json").exists()
if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}
android {
    namespace = "com.lanu.yalan"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.lanu.yalan"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("lanu.versionCode").orNull?.toInt() ?: 7
        versionName = providers.gradleProperty("lanu.versionName").orNull ?: "0.5.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!uiSmoke) {
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON") } }
        }
        buildConfigField("boolean", "UI_SMOKE", uiSmoke.toString())
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
    }
    signingConfigs {
        if (hasProductionSigning) create("production") {
            storeFile = file(System.getenv("LANU_KEYSTORE"))
            storePassword = System.getenv("LANU_STORE_PASSWORD")
            keyAlias = System.getenv("LANU_KEY_ALIAS")
            keyPassword = System.getenv("LANU_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { if (hasProductionSigning) signingConfig = signingConfigs.getByName("production") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("production")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    if (!uiSmoke) externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
if (hasProductionSigning) tasks.matching { it.name == "preDebugBuild" || it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyProductionSigning)
}
kapt { correctErrorTypes = true }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-compiler:2.56.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    if (firebaseEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
        implementation("com.google.firebase:firebase-crashlytics")
    }
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
}
