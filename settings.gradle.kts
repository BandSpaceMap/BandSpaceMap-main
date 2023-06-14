rootProject.name = "BeatMaps"

if (File("../bandspace-common-mp").exists()) {
    includeBuild("../bandspace-common-mp") {
        dependencySubstitution {
            substitute(module("io.beatmaps:BeatMaps-CommonMP")).using(project(":"))
        }
    }
}
