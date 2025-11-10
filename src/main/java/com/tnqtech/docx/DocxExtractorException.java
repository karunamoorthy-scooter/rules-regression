package com.tnqtech.docx;

/**
 * Checked exception raised when the extractor encounters an unrecoverable error.
 */
public class DocxExtractorException extends Exception {

    public DocxExtractorException(final String message) {
        super(message);
    }

    public DocxExtractorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
