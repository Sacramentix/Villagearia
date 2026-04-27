plugins {
    java
    jacoco
}
tasks.register<JavaExec>("myExec") {
    mainClass.set("Main")
    jacoco.applyTo(this)
    println("Jacoco extension: " + extensions.findByName("jacoco"))
}
