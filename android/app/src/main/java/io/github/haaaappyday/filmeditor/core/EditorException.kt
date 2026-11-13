package io.github.haaaappyday.filmeditor.core

/** Translate to localized messages at the UI boundary, not inside services. */
enum class EditorErrorCode {
    UNSUPPORTED_IMAGE, IMAGE_TOO_LARGE, READ_FAILED, INVALID_PARAMS,
    PRESET_UNAVAILABLE, RENDER_FAILED, EXPORT_FAILED,
}

class EditorException(val code: EditorErrorCode, cause: Throwable? = null)
    : Exception(code.name, cause)
