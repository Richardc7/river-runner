plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}
tasks.register<JavaExec>("strategyBenchmark") {
    group = "verification"
    description = "Paired-deck exploratory strategy benchmark; no profit guarantee."
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.riverrunner.engine.strategy.SimulationBenchmark")
    args(providers.gradleProperty("benchmarkBlocks").getOrElse("8"), providers.gradleProperty("benchmarkHands").getOrElse("100"))
}
