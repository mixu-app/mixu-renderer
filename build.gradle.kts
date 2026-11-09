// SPDX-License-Identifier: Apache-2.0

plugins {
    id("mixu.kotlin.multiplatform")
    id("mixu.quality")
    id("mixu.reproducible")
    id("mixu.maven.publish")
}

group = "app.mixu"
version = "0.0.1"
description = "Compose Canvas graph rendering, layout, and E-Ink policy for Mixu."

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("app.mixu:mixu-graph")
        }
    }
}
