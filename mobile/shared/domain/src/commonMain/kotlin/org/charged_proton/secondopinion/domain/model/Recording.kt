package org.charged_proton.secondopinion.domain.model

/**
 * A captured symptom recording stored on the device.
 *
 * Platform-agnostic: the file is referenced by its absolute path rather than
 * a platform file type so this model can live in commonMain.
 */
data class Recording(
    val filePath: String,
    val createdAtEpochMillis: Long,
    /** Speech duration after VAD trimming; upload metadata for the backend. */
    val durationMillis: Long = 0,
)
