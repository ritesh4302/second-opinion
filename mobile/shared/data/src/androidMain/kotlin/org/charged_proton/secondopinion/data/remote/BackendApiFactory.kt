package org.charged_proton.secondopinion.data.remote

import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android factory so the app module can build a [BackendApi] without
 * depending on Ktor types (the client stays an implementation detail).
 */
fun createBackendApi(
    baseUrl: String,
    tokenProvider: suspend () -> String? = { null },
    onUnauthorized: suspend () -> Unit = {},
): BackendApi =
    BackendApi(backendHttpClient(OkHttp.create(), tokenProvider, onUnauthorized), baseUrl)
