plugins {
    java
    id("com.diffplug.spotless") version "8.1.0"
    id("org.openrewrite.rewrite") version "6.20.0"
}

group = property("group").toString()
version = property("version").toString()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    // Add Hytale server JAR as a file dependency
    flatDir {
        dirs("/tmp/")
    }
}

dependencies {
    // Hytale Server API
    compileOnly(files("/tmp/HytaleServer.jar"))

    // Netty (provided by Hytale server at runtime)
    compileOnly("io.netty:netty-transport-native-epoll:4.1.118.Final:linux-x86_64")

    // OpenRewrite dependencies
    rewrite(platform("org.openrewrite.recipe:rewrite-recipe-bom:2.24.0"))
    rewrite("org.openrewrite.recipe:rewrite-static-analysis:2.24.0")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

spotless {
    java {
        target("src/**/*.java")
        importOrder()
        removeUnusedImports()
        googleJavaFormat().reorderImports(false)
        formatAnnotations()
    }
}

rewrite {
    activeRecipe("org.openrewrite.staticanalysis.CodeCleanup")
    isExportDatatables = true
}
