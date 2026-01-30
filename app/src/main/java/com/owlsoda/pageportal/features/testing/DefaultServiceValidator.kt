package com.owlsoda.pageportal.features.testing

import com.owlsoda.pageportal.services.BookService
import com.owlsoda.pageportal.services.ServiceFeature
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class DefaultServiceValidator @Inject constructor() : ServiceValidator {

    override suspend fun validateConnection(service: BookService): ValidationResult {
        return try {
            var latency = 0L
            latency = measureTimeMillis {
                // We should test auth or a lightweigh call. 
                // Since we don't have direct re-auth without creds here, we assume service is configured.
                // call listBooks with minimal size
                 service.listBooks(page = 0, pageSize = 1)
            }
            ValidationResult.Success("Connection active", latency)
        } catch (e: Exception) {
            ValidationResult.Failure("Connection failed: ${e.message}", e)
        }
    }

    override suspend fun validateListing(service: BookService): ValidationResult {
        return try {
            val start = System.currentTimeMillis()
            val books = service.listBooks(page = 0, pageSize = 10)
            val duration = System.currentTimeMillis() - start
            
            if (books.isEmpty()) {
                ValidationResult.Success("Listing worked (0 books found)", duration)
            } else {
                ValidationResult.Success("Listing worked (${books.size} books found)", duration)
            }
        } catch (e: Exception) {
            ValidationResult.Failure("Listing failed: ${e.message}", e)
        }
    }

    override suspend fun validateDetails(service: BookService): ValidationResult {
        return try {
             val books = service.listBooks(page = 0, pageSize = 1)
             if (books.isEmpty()) return ValidationResult.Skipped
             
             val bookId = books.first().serviceId
             val start = System.currentTimeMillis()
             val details = service.getBookDetails(bookId)
             val duration = System.currentTimeMillis() - start
             
             if (details.book.title.isNotBlank()) {
                 ValidationResult.Success("Details fetched for '${details.book.title}'", duration)
             } else {
                 ValidationResult.Failure("Details fetched but empty title")
             }
        } catch (e: Exception) {
            ValidationResult.Failure("Details check failed: ${e.message}", e)
        }
    }

    override suspend fun validateDownloadHeaders(service: BookService): ValidationResult {
        if (!service.supportsFeature(ServiceFeature.DOWNLOAD)) return ValidationResult.Skipped
        
        // This is harder to test without triggering actual download.
        // We might implementation specialized check later.
        return ValidationResult.Skipped
    }

    override suspend fun validateSyncCapabilities(service: BookService): ValidationResult {
        if (!service.supportsFeature(ServiceFeature.PROGRESS_SYNC)) return ValidationResult.Skipped
        
        // Check if getProgress works
         return try {
             val books = service.listBooks(page = 0, pageSize = 1)
             if (books.isEmpty()) return ValidationResult.Skipped
             
             val bookId = books.first().serviceId
             val start = System.currentTimeMillis()
             val progress = service.getProgress(bookId)
             val duration = System.currentTimeMillis() - start
             
             ValidationResult.Success("Progress fetched (${progress?.percentComplete ?: 0}%)", duration)
        } catch (e: Exception) {
            ValidationResult.Failure("Progress sync check failed: ${e.message}", e)
        }
    }
}
