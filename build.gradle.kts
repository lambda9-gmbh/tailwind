plugins {
}

allprojects {
    val version = (property("version") as? String).let { version ->
        if (version == null || version == "unspecified") "0.2.0"
        else version
    }
    setProperty("version", version)
}
