package com.doova.ktab.enums.message;

import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Getter
public enum ApiMessageKey {

    // ===== AUTH =====
    AUTH_REGISTER_SUCCESS("auth.register.success"), AUTH_LOGIN_SUCCESS("auth.login.success"), AUTH_EMAIL_VERIFIED("auth.email.verified"), AUTH_INVALID_CREDENTIALS("auth.invalid.credentials"), AUTH_EMAIL_ALREADY_USED("auth.email.already.used"), AUTH_ACCOUNT_ALREADY_ACTIVE("auth.account.already.active"), AUTH_INVALID_OR_EXPIRED_CODE("auth.code.invalid.or.expired"), AUTH_RESET_CODE_SENT("auth.reset.code.sent"), AUTH_PASSWORD_RESET_SUCCESS("auth.password.reset.success"),

    // ===== AUTHOR =====
    AUTHOR_ANALYTICS_SUCCESS("author.analytics.success"), AUTHOR_BOOK_ANALYTICS_SUCCESS("author.book.analytics.success"),

    // ===== VALIDATION =====
    VALIDATION_FAILED("validation.failed"), MISSING_PARAMETER("validation.missing.parameter"),

    // ===== GENERIC =====
    RESOURCE_NOT_FOUND("resource.not.found"), ACCESS_DENIED("access.denied"), INTERNAL_ERROR("internal.error"), OPERATION_SUCCESS("operation.success"),

    // ===== AUTHOR BOOK =====
    AUTHOR_BOOKS_FETCH_SUCCESS("author.books.fetch.success"), AUTHOR_BOOK_FETCH_SUCCESS("author.book.fetch.success"), AUTHOR_BOOK_CREATE_SUCCESS("author.book.create.success"), AUTHOR_BOOK_UPDATE_SUCCESS("author.book.update.success"), AUTHOR_BOOK_DELETE_SUCCESS("author.book.delete.success"), AUTHOR_BOOK_NOT_FOUND("author.book.not.found"), AUTHOR_BOOK_UPDATE_FORBIDDEN("author.book.update.forbidden"), AUTHOR_BOOK_NOT_OWNER("author.book.not.owner"), AUTHOR_BOOK_FILE_UPLOAD_FAILED("author.book.file.upload.failed"),

    // ===== FILE =====
    FILE_INVALID_IMAGE("file.invalid.image"), FILE_INVALID_PDF("file.invalid.pdf"), FILE_UPLOAD_FAILED("file.upload.failed"), FILE_DELETE_FAILED("file.delete.failed"),

    // ===== GENRE =====
    GENRE_NOT_FOUND("genre.not.found"), GENRE_CREATED_SUCCESS("genre.created.success"), GENRE_UPDATED_SUCCESS("genre.updated.success"), GENRE_CREATE_FAILED("genre.create.failed"), GENRE_UPDATE_FAILED("genre.update.failed"), GENRE_FETCH_SUCCESS("genre.fetch.success"),

    // ===== LIBRARY =====
    LIBRARY_BOOK_ADDED("library.book.added"), LIBRARY_BOOK_REMOVED("library.book.removed"), LIBRARY_BOOK_ALREADY_EXISTS("library.book.already.exists"), LIBRARY_BOOK_NOT_FOUND("library.book.not.found"), LIBRARY_BOOK_NOT_IN_LIBRARY("library.book.not.in.library"), LIBRARY_USER_NOT_FOUND("library.user.not.found"), LIBRARY_FETCH_SUCCESS("library.fetch.success"), LIBRARY_CHECK_SUCCESS("library.check.success"),

    // ===== READER / DISCOVERY =====
    READER_BOOKS_FETCH_SUCCESS("reader.books.fetch.success"), READER_BOOK_FETCH_SUCCESS("reader.book.fetch.success"), READER_BOOK_NOT_FOUND("reader.book.not.found"), READER_SEARCH_SUCCESS("reader.search.success"), READER_SIMILAR_SUCCESS("reader.similar.success"),

    // ===== REVIEWS =====
    REVIEW_CREATE_SUCCESS("review.create.success"), REVIEW_UPDATE_SUCCESS("review.update.success"), REVIEW_DELETE_SUCCESS("review.delete.success"), REVIEW_FETCH_SUCCESS("review.fetch.success"), REVIEW_IS_REVIEWED_SUCCESS("review.isReviewed.success"),

    REVIEW_BOOK_NOT_FOUND("review.book.not.found"), REVIEW_ALREADY_EXISTS("review.already.exists"), REVIEW_NOT_FOUND("review.not.found"),

    // ===== AI CONCLUSION =====
    AI_CONCLUSION_GENERATE_SUCCESS("ai.conclusion.generate.success"), AI_CONCLUSION_INVALID_FILE("ai.conclusion.invalid.file"), AI_CONCLUSION_UNEXPECTED_ERROR("ai.conclusion.unexpected.error"), AI_CONCLUSION_STREAM_ERROR("ai.conclusion.stream.error"),

    // ===============================
    // AI / BOOK ENDING
    // ===============================
    AI_BOOK_ENDING_GENERATE_SUCCESS("ai.book.ending.generate.success"), AI_BOOK_ENDING_INVALID_FILE("ai.book.ending.invalid.file"), AI_BOOK_ENDING_INVALID_AUDIENCE("ai.book.ending.invalid.audience"), AI_BOOK_ENDING_UNEXPECTED_ERROR("ai.book.ending.unexpected.error"),

    // ===== IMAGE VALIDATION =====
    IMAGE_INVALID_FORMAT("image.invalid.format"), IMAGE_SIZE_EXCEEDED("image.size.exceeded"), IMAGE_INVALID_FILE("image.invalid.file"), IMAGE_INVALID_RATIO("image.invalid.ratio"),

    // ===== PDF VALIDATION =====
    PDF_INVALID_FILENAME("pdf.invalid.filename"), PDF_INVALID_EXTENSION("pdf.invalid.extension"), PDF_INVALID_MIME("pdf.invalid.mime"), PDF_SIZE_EXCEEDED("pdf.size.exceeded"), PDF_ENCRYPTED("pdf.encrypted"), PDF_EMPTY("pdf.empty"), PDF_PAGES_EXCEEDED("pdf.pages.exceeded"), PDF_NO_SELECTABLE_TEXT("pdf.no.selectable.text"), PDF_CORRUPTED("pdf.corrupted"),

    // ===== SECURITY =====
    SECURITY_ACCESS_DENIED("security.access.denied"), SECURITY_INVALID_USER_MATCH("security.invalid.user.match"),

    // ===============================
    // S3 / FILE STORAGE
    // ===============================
    S3_UPLOAD_UNEXPECTED_ERROR("s3.upload.unexpected_error"), S3_UPLOAD_INVALID_IMAGE("s3.upload.invalid_image"), S3_UPLOAD_INVALID_PDF("s3.upload.invalid_pdf"), S3_DELETE_UNEXPECTED_ERROR("s3.delete.unexpected_error"),

    SECURITY_UNAUTHORIZED("security.unauthorized"), SECURITY_INVALID_TOKEN("security.invalid.token"), SECURITY_AUTH_REQUIRED("security.authentication.required"),

    // ===============================
    // SECURITY
    // ===============================
    SECURITY_FORBIDDEN("security.forbidden"), SECURITY_TOKEN_INVALID("security.token.invalid"), SECURITY_TOKEN_MISSING("security.token.missing"), SECURITY_AUTHENTICATION_FAILED("security.authentication.failed"),

    // =========================================================
    // BOOK TEXT
    // =========================================================

    BOOK_TEXT_FETCH_SUCCESS("book.text.fetch.success"), BOOK_TEXT_RANGE_FETCH_SUCCESS("book.text.range.fetch.success"), BOOK_TEXT_PAGE_FETCH_SUCCESS("book.text.page.fetch.success"), BOOK_TEXT_STATS_FETCH_SUCCESS("book.text.stats.fetch.success"),

    // =========================================================
    // INTERACTIVE STORY
    // =========================================================

    STORY_CREATE_SUCCESS("story.create.success"), STORY_FETCH_SUCCESS("story.fetch.success"), STORY_FETCH_ALL_SUCCESS("story.fetch.all.success"), STORY_FETCH_MY_SUCCESS("story.fetch.my.success"), STORY_UPDATE_SUCCESS("story.update.success"), STORY_DELETE_SUCCESS("story.delete.success"), STORY_NOT_FOUND("story.not.found"), STORY_HAS_SESSIONS("story.has.sessions"), STORY_NOT_OWNER("story.not.owner"),

    // =========================================================
    // INTERACTIVE STORY SESSION
    // =========================================================

    SESSION_START_SUCCESS("session.start.success"), SESSION_CHOOSE_SUCCESS("session.choose.success"),

    // =========================================================
    // LIBRARY ORGANIZATION & LIBRARIAN
    // =========================================================

    LIBRARY_ORGANIZATION_NOT_FOUND("library.organization.not.found"),
    LIBRARY_ORGANIZATION_NOT_ASSOCIATED("library.organization.not.associated"),
    LIBRARY_ORGANIZATION_ACCESS_DENIED("library.organization.access.denied"),
    LIBRARY_ORGANIZATION_CREATED_SUCCESS("library.organization.created.success"),
    LIBRARY_ORGANIZATION_UPDATED_SUCCESS("library.organization.updated.success"),
    LIBRARY_ORGANIZATION_FETCH_SUCCESS("library.organization.fetch.success"),

    LIBRARY_STAFF_ASSIGNED_SUCCESS("library.staff.assigned.success"),
    LIBRARY_STAFF_REMOVED_SUCCESS("library.staff.removed.success"),
    LIBRARY_STAFF_FETCH_SUCCESS("library.staff.fetch.success"),
    LIBRARY_STAFF_CANNOT_REMOVE_SELF("library.staff.cannot.remove.self"),
    LIBRARY_STAFF_NOT_FOUND("library.staff.not.found"),

    LIBRARIAN_BOOK_CREATE_SUCCESS("librarian.book.create.success"),
    LIBRARIAN_BOOK_UPDATE_SUCCESS("librarian.book.update.success"),
    LIBRARIAN_BOOK_DELETE_SUCCESS("librarian.book.delete.success"),
    LIBRARIAN_BOOK_FETCH_SUCCESS("librarian.book.fetch.success"),
    LIBRARIAN_BOOKS_FETCH_SUCCESS("librarian.books.fetch.success"),
    LIBRARIAN_BOOK_NOT_FOUND("librarian.book.not.found"),
    LIBRARIAN_BOOK_ACCESS_DENIED("librarian.book.access.denied"),

    AUTH_ROLE_REGISTRATION_FORBIDDEN("auth.role.registration.forbidden"),
    AUTH_GOOGLE_LOGIN_FAILED("auth.google.login.failed"),
    USER_NOT_FOUND("user.not.found"),
    GENRE_MAIN_NOT_FOUND("genre.main.not.found"),
    GENRE_SUB_NOT_FOUND("genre.sub.not.found");


    private final String key;

    ApiMessageKey(String key) {
        this.key = key;
    }

    public String getMessage(MessageSource messageSource, Object... args) {
        return messageSource.getMessage(this.key, args, LocaleContextHolder.getLocale());
    }

}
