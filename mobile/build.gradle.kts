// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("org.openapi.generator") version "7.10.0"
}

// Task opcional para regenerar DTOs Kotlin a partir del OpenAPI del backend.
// NO corre en el build normal — solo cuando el usuario lo invoca con
//   ./gradlew generateApi
// La idea es tener los DTOs como fuente de verdad del backend disponibles
// cuando interese, sin forzar que el CI dependa de un backend arrancado.
//
// Flujo recomendado: con el backend corriendo en local, exportar el spec:
//   curl http://localhost:8080/api/v1/v3/api-docs -o openapi.json
// y luego ./gradlew generateApi genera Kotlin bajo build/generated/openapi.
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/openapi.json")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.musarum.mobile.api.generated")
    modelPackage.set("com.musarum.mobile.api.generated.model")
    invokerPackage.set("com.musarum.mobile.api.generated.infra")
    configOptions.set(mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "string"
    ))
}

tasks.register("generateApi") {
    group = "openapi"
    description = "Genera DTOs Kotlin desde openapi.json en la raíz del repo"
    dependsOn("openApiGenerate")
}
