package io.github.alirezajavan.pulsekit.core.permission

/**
 * Platform-agnostic grant state for a [Permission].
 */
enum class PermissionStatus {
    /** The permission is granted and the feature is usable. */
    GRANTED,

    /** The permission has been denied by the user. */
    DENIED,

    /** The permission is denied and the OS will no longer show a prompt. */
    DENIED_PERMANENTLY,

    /** The status is not yet known (prompt hasn't been shown). */
    NOT_DETERMINED,
}
