package com.subtitleplayer.prototype

internal enum class VideoScaleMode(val label: String, val description: String) {
    Fit("Fit", "Shows the full video at its original aspect ratio, with black bars if needed."),
    Crop("Crop", "Fills the screen while preserving the aspect ratio. Edges may be cropped."),
    Stretch("Stretch", "Stretches the video to fill the screen.")
}
