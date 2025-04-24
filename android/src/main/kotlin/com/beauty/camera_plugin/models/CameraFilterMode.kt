package com.beauty.camera_plugin.models

enum class CameraFilterMode {
    NONE,
    BEAUTY,
    MONO,
    NEGATIVE,
    SEPIA,
    SOLARIZE,
    POSTERIZE,
    WHITEBOARD,
    BLACKBOARD,
    AQUA,
    EMBOSS,
    SKETCH,
    NEON,
    VINTAGE,
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
    SHARPEN,
    GAUSSIAN_BLUR,
    VIGNETTE,
    HUE,
    EXPOSURE,
    HIGHLIGHT_SHADOW,
    LEVELS,
    COLOR_BALANCE,
    LOOKUP;

    companion object {
        fun fromPigeon(mode: com.beauty.camera_plugin.CameraFilterMode): CameraFilterMode {
            return valueOf(mode.name.uppercase())
        }

//        fun toPigeon(mode: CameraFilterMode): com.beauty.camera_plugin.CameraFilterMode {
//            return com.beauty.camera_plugin.CameraFilterMode.valueOf(mode.name.lowercase())
//        }
    }
} 