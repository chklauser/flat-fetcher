
buildscript {
    dependencies {
        classpath( "org.hibernate:hibernate-gradle-plugin:5.4.18.Final")
    }
}

plugins {
    java
    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.freefair.lombok") version "5.1.0"
}

apply(plugin = "io.spring.dependency-management")
apply(plugin = "org.hibernate.orm")

group = "link.klauser.flat-fetcher"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.hibernate:hibernate-gradle-plugin:5.4.18.Final")

    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mapstruct:mapstruct:1.4.0.Beta1")
    testRuntimeOnly("com.h2database:h2") {
        exclude(group="org.junit.vintage", module="junit-vintage-engine")
    }
    testCompileOnly("org.hibernate:hibernate-jpamodelgen")
    testRuntimeOnly("ch.qos.logback:logback-core:1.2.3")
}

tasks.compileTestJava.configure {
    options.annotationProcessorGeneratedSourcesDirectory = file("$buildDir/generated/sources/java")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}

configure<org.hibernate.orm.tooling.gradle.HibernateExtension> {
    sourceSet(project.sourceSets.findByName("test"))
    enhance(closureOf<org.hibernate.orm.tooling.gradle.EnhanceExtension> {
        enableLazyInitialization = true
    })
}

tasks.bootJar.configure {
    enabled = false
}