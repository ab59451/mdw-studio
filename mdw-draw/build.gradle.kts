import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.centurylink.mdw"
version = "2.2.3"

sourceSets.main {
    withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs("src")
    }
    resources.srcDirs("resources")
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    compile("com.centurylink.mdw:mdw-common:6.1.36-SNAPSHOT") { isTransitive = false }
    // compile(files("../../mdw/mdw/deploy/app/mdw-common-6.1.36-SNAPSHOT.jar"))
    compile("io.limberest:limberest:1.2.5") { isTransitive = false }
    compile("org.json:json:20180130")
    compile("com.google.code.gson:gson:2.8.5")
    compile("com.vladsch.flexmark:flexmark:0.32.22")
    compile("com.vladsch.flexmark:flexmark-util:0.32.22")
    compile("com.vladsch.flexmark:flexmark-ext-anchorlink:0.32.22")
    compile("com.vladsch.flexmark:flexmark-ext-autolink:0.32.22")
    compile("com.vladsch.flexmark:flexmark-ext-superscript:0.32.22")
    compile("com.vladsch.flexmark:flexmark-ext-tables:0.32.22")
    compile("com.vladsch.flexmark:flexmark-ext-typographic:0.32.22")
    compile("com.vladsch.flexmark:flexmark-formatter:0.32.22")
    compile("org.slf4j:slf4j-api:1.7.25")
    compile("com.jayway.jsonpath:json-path:2.4.0")
    compile("com.github.librepdf:openpdf:1.3.13")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
