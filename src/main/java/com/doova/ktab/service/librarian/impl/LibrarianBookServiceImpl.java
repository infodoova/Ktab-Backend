package com.doova.ktab.service.librarian.impl;

import com.doova.ktab.dto.library.LibrarianBookUploadRequest;
import com.doova.ktab.dto.library.UpdateLibrarianBookRequest;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.genre.MainGenreRepository;
import com.doova.ktab.repository.genre.SubGenreRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.book.BookFileService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.librarian.LibrarianBookService;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibrarianBookServiceImpl implements LibrarianBookService {

    private final BookRepository bookRepository;
    private final MainGenreRepository mainGenreRepository;
    private final SubGenreRepository subGenreRepository;
    private final UserRepository userRepository;
    private final BookFileService bookFileService;
    private final BookResponseBuilderService responseBuilder;
    private final com.doova.ktab.service.file.AttachmentService attachmentService;
    private final com.doova.ktab.service.file.FileStorageService fileStorageService;

    // =========================================================================
    // TENANCY HELPER
    // =========================================================================
    private User requireManagedLibrarian(User librarian) {
        if (librarian == null || librarian.getId() == null) {
            log.warn("Access denied: Librarian principal is null");
            throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
        }
        User managed = userRepository.findById(librarian.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));
        if (managed.getLibraryOrganization() == null) {
            log.warn("Access denied: User {} has no associated library organization", managed.getEmail());
            throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
        }
        return managed;
    }

    private LibraryOrganization requireLibrarianOrganization(User librarian) {
        return requireManagedLibrarian(librarian).getLibraryOrganization();
    }

    // =========================================================================
    // CREATE BOOK (Librarian)
    // =========================================================================
    @Override
    @Transactional
    public BookResponseDto createBook(
            LibrarianBookUploadRequest req,
            MultipartFile cover,
            MultipartFile pdf,
            User librarian
    ) {
        User managedLibrarian = requireManagedLibrarian(librarian);
        LibraryOrganization libraryOrg = managedLibrarian.getLibraryOrganization();

        Book book = new Book();
        book.setTitle(req.getTitle());
        book.setDescription(req.getDescription());
        book.setCustomAuthorName(req.getCustomAuthorName());
        book.setBookSource(BookSource.LIBRARY);
        book.setLibraryOrganization(libraryOrg);
        book.setUploader(managedLibrarian);
        book.setAuthor(null); // Institutional upload - no user author
        book.setLanguage(req.getLanguage());
        book.setAgeRangeMin(req.getAgeRangeMin());
        book.setAgeRangeMax(req.getAgeRangeMax());
        book.setPageCount(req.getPageCount());
        book.setHasAudio(Boolean.TRUE.equals(req.getHasAudio()));
        book.setStatus(req.getStatus() != null ? req.getStatus() : BookStatus.DRAFT);

        book.setMainGenre(
                mainGenreRepository.findById(req.getMainGenreId())
                        .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.GENRE_MAIN_NOT_FOUND))
        );

        book.setSubGenre(
                subGenreRepository.findById(req.getSubGenreId())
                        .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.GENRE_SUB_NOT_FOUND))
        );

        Book savedBook = bookRepository.save(book);

        // Upload and bind attachment files (S3 / storage)
        bookFileService.handleCreateFiles(savedBook, cover, pdf);

        log.info("Librarian {} created book {} for library {}", librarian.getEmail(), savedBook.getId(), libraryOrg.getName());
        return responseBuilder.build(savedBook, true);
    }

    // =========================================================================
    // UPDATE BOOK (Librarian)
    // =========================================================================
    @Override
    @Transactional
    public BookResponseDto updateBook(
            Long id,
            UpdateLibrarianBookRequest req,
            MultipartFile cover,
            MultipartFile pdf,
            User librarian
    ) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);

        Book book = bookRepository.findByIdAndLibraryOrganizationId(id, libraryOrg.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARIAN_BOOK_NOT_FOUND));

        if (req.getTitle() != null && !req.getTitle().isBlank()) book.setTitle(req.getTitle());
        if (req.getDescription() != null) book.setDescription(req.getDescription());
        if (req.getCustomAuthorName() != null && !req.getCustomAuthorName().isBlank()) {
            book.setCustomAuthorName(req.getCustomAuthorName());
        }
        if (req.getLanguage() != null) book.setLanguage(req.getLanguage());
        if (req.getAgeRangeMin() != null) book.setAgeRangeMin(req.getAgeRangeMin());
        if (req.getAgeRangeMax() != null) book.setAgeRangeMax(req.getAgeRangeMax());
        if (req.getPageCount() != null) book.setPageCount(req.getPageCount());
        if (req.getHasAudio() != null) book.setHasAudio(req.getHasAudio());
        if (req.getStatus() != null) book.setStatus(req.getStatus());

        if (req.getMainGenreId() != null) {
            book.setMainGenre(
                    mainGenreRepository.findById(req.getMainGenreId())
                            .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.GENRE_MAIN_NOT_FOUND))
            );
        }

        if (req.getSubGenreId() != null) {
            book.setSubGenre(
                    subGenreRepository.findById(req.getSubGenreId())
                            .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.GENRE_SUB_NOT_FOUND))
            );
        }

        bookFileService.handleUpdateFiles(book, cover, pdf);
        Book updatedBook = bookRepository.save(book);

        log.info("Librarian {} updated book {} for library {}", librarian.getEmail(), updatedBook.getId(), libraryOrg.getName());
        return responseBuilder.build(updatedBook, true);
    }

    // =========================================================================
    // GET BOOK BY ID (Librarian)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public BookResponseDto getBookByIdForLibrarian(Long id, User librarian) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);

        Book book = bookRepository.findByIdAndLibraryOrganizationId(id, libraryOrg.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARIAN_BOOK_NOT_FOUND));

        return responseBuilder.build(book, true);
    }

    // =========================================================================
    // LIST BOOKS FOR CURRENT LIBRARIAN'S ORGANIZATION
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getBooksForLibrary(int page, int size, String status, User librarian) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Page<Book> pageResult;
        if (StringUtils.hasText(status)) {
            pageResult = bookRepository.findAllByLibraryOrganizationIdAndStatus(
                    libraryOrg.getId(),
                    BookStatus.valueOf(status.toUpperCase()),
                    pageable
            );
        } else {
            pageResult = bookRepository.findAllByLibraryOrganizationId(libraryOrg.getId(), pageable);
        }

        return PageResponse.fromPage(pageResult.map(b -> responseBuilder.build(b, true)));
    }

    // =========================================================================
    // DELETE BOOK (Librarian)
    // =========================================================================
    @Override
    @Transactional
    public void deleteBook(Long id, User librarian) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);

        Book book = bookRepository.findByIdAndLibraryOrganizationId(id, libraryOrg.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARIAN_BOOK_NOT_FOUND));

        bookFileService.handleDeleteFiles(book);
        bookRepository.delete(book);
        log.info("Librarian {} deleted book {} from library {}", librarian.getEmail(), id, libraryOrg.getName());
    }

    // =========================================================================
    // PUBLIC READER: GET PUBLISHED BOOKS BY SPECIFIC LIBRARY
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getPublishedBooksByLibrary(Long libraryOrgId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"));
        Page<Book> pageResult = bookRepository.findAllByLibraryOrganizationIdAndStatus(
                libraryOrgId,
                BookStatus.PUBLISHED,
                pageable
        );

        return PageResponse.fromPage(pageResult.map(responseBuilder::build));
    }

    // =========================================================================
    // SEARCH LIBRARIAN BOOKS (ADVANCED MULTI-FACETED)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> searchLibrarianBooks(
            User librarian,
            com.doova.ktab.dto.book.LibrarianBookSearchRequest req,
            Pageable pageable
    ) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);
        Page<Book> pageResult = bookRepository.findAll(
                com.doova.ktab.specification.BookSpecification.forLibrarian(libraryOrg.getId(), req),
                pageable
        );
        return PageResponse.fromPage(pageResult.map(b -> responseBuilder.build(b, true)));
    }

    // =========================================================================
    // GET SOURCE FILE (AUDITED EPHEMERAL DOWNLOAD)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public com.doova.ktab.dto.book.BookSourceFileResponseDto getSourceFileForLibrarian(Long bookId, User librarian) {
        LibraryOrganization libraryOrg = requireLibrarianOrganization(librarian);

        Book book = bookRepository.findByIdAndLibraryOrganizationId(bookId, libraryOrg.getId())
                .orElseThrow(() -> {
                    log.warn("SECURITY_ALERT: Unauthorized attempt by Librarian User ID {} ({}) for Org ID {} to access source file of Book ID {}",
                            librarian.getId(), librarian.getEmail(), libraryOrg.getId(), bookId);
                    return new ResourceNotFoundException(ApiMessageKey.LIBRARIAN_BOOK_NOT_FOUND);
                });

        com.doova.ktab.model.attachment.Attachment pdf = attachmentService.getAttachment(book.getId(), "Book", "PDF_SOURCE")
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.BOOK_OCR_PDF_MISSING));

        java.time.Duration ttl = java.time.Duration.ofMinutes(3);
        String downloadUrl = fileStorageService.getPreSignedDownloadUrl(pdf.getStoragePath(), ttl, pdf.getFileName());

        log.info("SECURITY_AUDIT: Librarian User ID {} ({}) for Org ID {} generated ephemeral download URL for Book ID {} ({})",
                librarian.getId(), librarian.getEmail(), libraryOrg.getId(), book.getId(), pdf.getFileName());

        return new com.doova.ktab.dto.book.BookSourceFileResponseDto(
                book.getId(),
                pdf.getFileName(),
                downloadUrl,
                java.time.Instant.now().plus(ttl)
        );
    }
}
