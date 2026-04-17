import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("java")
    id("idea")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("org.jetbrains.kotlin.kapt") version "1.8.10"
    id("org.jetbrains.kotlin.plugin.spring") version "1.8.10"
    id("org.springframework.boot") version "3.0.5"
}

group = "ua.blink.whatsappgraff"
version = "1.0.24"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("kotlin-spring")
        plugin("maven-publish")
        plugin("idea")
        plugin("org.springframework.boot")
        plugin("io.spring.dependency-management")
    }

    dependencies {
        // Kotlin
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-script-util")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
        implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

        implementation("net.java.dev.jna:jna:5.11.0")
    }

    configure<DependencyManagementExtension> {
        val springBootVersion = "3.0.5"
        imports { mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}") }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    // IDEA
    idea {
        module {
            val kaptMain = file("build/generated/source/kapt/main")
            sourceDirs.add(kaptMain)
            generatedSourceDirs.add(kaptMain)
        }
    }

    // Jar
    tasks.getByName<Jar>("jar") {
        enabled = true
        manifest {
            attributes["Implementation-Version"] = archiveVersion
        }
        archiveFileName.set(rootProject.name + ".jar")
    }

    tasks {
        // Kotlin
        compileKotlin {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
                jvmTarget = JavaVersion.VERSION_17.majorVersion
            }
        }
        compileTestKotlin {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
                jvmTarget = JavaVersion.VERSION_17.majorVersion
            }
        }

        val sourcesJar by creating(Jar::class) {
            dependsOn(JavaPlugin.CLASSES_TASK_NAME)
            archiveClassifier.set("sources")
            from(sourceSets["main"].allSource)
        }

        artifacts {
            add("archives", sourcesJar)
        }

        bootJar {
            enabled = false
        }
    }

    // Configure the publishing plugin
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                groupId = rootProject.group.toString()
                artifactId = project.name
                // Add the following line inside the subprojects block
                version = rootProject.version.toString()

                from(components["java"])

                pom {
                    val GITHUB_TELEGRAFF_URL: String by project
                    name.set("whatsappgraff")
                    description.set("Kotlin DSL for WhatsApp bot development")
                    url.set(GITHUB_TELEGRAFF_URL)
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("gazanfarov")
                            name.set("Ruslan Gazanfarov")
                            email.set("ruslan.gazanfarov@eventmate.email")
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                val GITHUB_TELEGRAFF_URL: String by project
                url = uri(GITHUB_TELEGRAFF_URL)
                credentials {
                    username = System.getenv("GITHUB_USERNAME")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

}
