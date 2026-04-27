plugins {
    java
    jacoco
    eclipse
    idea
}

jacoco {
    toolVersion = "0.8.14" // Use latest JaCoCo to support newer Java versions
}

// 1. Create a specific task for running the server with coverage
tasks.register<JavaExec>("runServerWithCoverage") {
    group = "Hytale"
    description = "Runs the devserver with JaCoCo coverage enabled, use -Ddebug for opening a debugger."
    
    // Copy the configuration from the original runServer task
    val rs = tasks.named<JavaExec>("runServer").get()
    mainClass.set(rs.mainClass)
    classpath = rs.classpath
    args = rs.args
    jvmArgs = rs.jvmArgs
    workingDir = rs.workingDir
    standardInput = System.`in`

    // Apply the Jacoco agent to this specific task
    jacoco.applyTo(this)
    extensions.configure<JacocoTaskExtension>("jacoco") {
        isEnabled = true
        destinationFile = layout.buildDirectory.file("jacoco/runServerWithCoverage.exec").get().asFile
    }
}

tasks.named<JavaExec>("runServer") {
    standardInput = System.`in`
}

// 2. Create a custom task to generate the report from the new coverage task.
tasks.register<JacocoReport>("jacocoServerReport") {
    group = "verification"
    description = "Generates a JaCoCo coverage report from the runServerWithCoverage execution data."
    
    val execFile = layout.buildDirectory.file("jacoco/runServerWithCoverage.exec").get().asFile
    
    onlyIf { execFile.exists() }
    
    executionData(execFile)
    sourceSets(sourceSets.main.get())
    
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtmlServer"))
    }
}

repositories {
    mavenCentral()
}

eclipse {
    classpath {
        defaultOutputDir = file("bin/main")
    }
}

tasks.register("generateHytaleVersionFile") {
    group = "build"
    description = "Generates a file containing the Hytale server version."
    
    val outputDir = layout.buildDirectory.dir("generated")
    val versionFile = outputDir.map { it.file("hytaleServer.version") }
    outputs.file(versionFile)

    doLast {
        val configuration = configurations.named("compileClasspath").get()
        
        if (!configuration.isCanBeResolved) {
            throw GradleException("compileClasspath cannot be resolved")
        }

        val hytaleServerArtifact = configuration.resolvedConfiguration.resolvedArtifacts
            .find { it.moduleVersion.id.group == "com.hypixel.hytale" && it.moduleVersion.id.name == "Server" }
        
        val hytaleServerVersion = hytaleServerArtifact?.moduleVersion?.id?.version
            ?: throw GradleException("Could not find com.hypixel.hytale:Server artifact in compileClasspath")
        
        val file = versionFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(hytaleServerVersion)
        println("Generated hytaleServer.version: $hytaleServerVersion at ${file.absolutePath}")
    }
}

tasks.named("build") {
    dependsOn("generateHytaleVersionFile")
}
