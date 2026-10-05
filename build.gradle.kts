// Root build file. Plugins are declared here without applying so the version
// catalog stays in one place; the :app module applies them.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
