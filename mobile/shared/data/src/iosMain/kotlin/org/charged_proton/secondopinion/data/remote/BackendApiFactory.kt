package org.charged_proton.secondopinion.data.remote

import io.ktor.client.engine.darwin.Darwin

/**
 * iOS factory so the app can build a [BackendApi] without depending on Ktor
 * types (the client stays an implementation detail). Darwin (NSURLSession)
 * engine, mirroring the OkHttp factory in androidMain.
 */
fun createBackendApi(
    baseUrl: String,
    tokenProvider: suspend () -> String? = { null },
    onUnauthorized: suspend () -> Unit = {},
): BackendApi =
    BackendApi(backendHttpClient(Darwin.create(), tokenProvider, onUnauthorized), baseUrl)
