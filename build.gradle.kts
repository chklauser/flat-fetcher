// Copyright 2020 Christian Klauser
//
// Licensed under the Apache License,Version2.0(the"License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,software
// distributed under the License is distributed on an"AS IS"BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

buildscript {
    dependencies {
        classpath( "org.hibernate:hibernate-gradle-plugin:5.4.18.Final")
    }
}

plugins {
    java
    `java-library`
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
    api("jakarta.persistence:jakarta.persistence-api:2.2.3")
    api("org.jetbrains:annotations:19.0.0")
    compileOnly("org.hibernate:hibernate-core:5.4.18.Final")
    implementation("org.slf4j:slf4j-api:1.7.30")

    testImplementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
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