rootProject.name = "villagearia"

pluginManagement {
    if (file("gradle-scaffoldit-modkit").exists()) {
        includeBuild("gradle-scaffoldit-modkit")
    }
}

plugins {
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("release")
    useVersion("+")

    repositories {
    }

    dependencies {
    }

    manifest {
        Group = "Sacramentix"
        Name = "Villagearia"
        Main = "villagearia.Villagearia"
    }
}
