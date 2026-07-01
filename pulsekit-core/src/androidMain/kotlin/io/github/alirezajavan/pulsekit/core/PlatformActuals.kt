package io.github.alirezajavan.pulsekit.core

import java.util.UUID

internal actual fun platformGenerateUuid(): String = UUID.randomUUID().toString()

actual fun platformCurrentTimeMillis(): Long = System.currentTimeMillis()
