package io.github.alirezajavan.pulsekit.core

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal actual fun platformGenerateUuid(): String = NSUUID().UUIDString()

actual fun platformCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
