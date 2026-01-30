package com.owlsoda.pageportal.features.testing

import com.owlsoda.pageportal.services.BookService
import com.owlsoda.pageportal.services.ServiceFeature

sealed class ValidationResult {
    data class Success(val message: String, val latencyMs: Long) : ValidationResult()
    data class Failure(val message: String, val error: Throwable? = null) : ValidationResult()
    object Skipped : ValidationResult()
}

interface ServiceValidator {
    suspend fun validateConnection(service: BookService): ValidationResult
    suspend fun validateListing(service: BookService): ValidationResult
    suspend fun validateDetails(service: BookService): ValidationResult
    suspend fun validateDownloadHeaders(service: BookService): ValidationResult
    suspend fun validateSyncCapabilities(service: BookService): ValidationResult
}
