plugins {
    java
}

group = "com.revertplugin"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.2.1")
}


tasks {
    compileJava {
        options.release = 21
    }

    jar {
        from(configurations.runtimeClasspath.map { conf ->
            conf.map { if (it.isDirectory) it else zipTree(it) }
        })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        filesMatching("**/*.yml") {
            expand(mapOf(
                "version" to project.version,
                "description" to (project.description ?: "")
            ))
        }
    }
}
