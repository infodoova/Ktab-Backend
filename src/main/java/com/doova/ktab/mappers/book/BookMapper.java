package com.doova.ktab.mappers.book;

import com.doova.ktab.dto.book.BookRequestDto;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.genre.MainGenreRepository;
import com.doova.ktab.repository.genre.SubGenreRepository;
import com.doova.ktab.service.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Mapper interface for converting between Book Entity and Book DTOs using
 * MapStruct.
 * Defined as an abstract class to allow dependency injection (UserService) for
 * resolving the foreign key (User author) during DTO-to-Entity mapping.
 */
@Mapper(componentModel = "spring")
public abstract class BookMapper {

    @Autowired
    protected MainGenreRepository mainGenreRepository;

    @Autowired
    protected SubGenreRepository subGenreRepository;

    /**
     * Entity → Response DTO
     */
    public abstract BookResponseDto toResponseDto(Book book);

    public abstract List<BookResponseDto> toResponseDto(List<Book> books);

    /**
     * DTO → Entity (CREATE)
     */
    @Mapping(target = "author", ignore = true)
    public Book toEntity(BookRequestDto dto) {

        Book book = new Book();

        book.setTitle(dto.getTitle());
        book.setDescription(dto.getDescription());
        book.setLanguage(dto.getLanguage());
        book.setAgeRangeMin(dto.getAgeRangeMin());
        book.setAgeRangeMax(dto.getAgeRangeMax());
        book.setPageCount(dto.getPageCount());
        book.setHasAudio(dto.getHasAudio());
        book.setStatus(dto.getStatus());

        book.setMainGenre(
                mainGenreRepository.findById(dto.getMainGenreId())
                        .orElseThrow(() -> new EntityNotFoundException("Main genre not found")));

        book.setSubGenre(
                subGenreRepository.findById(dto.getSubGenreId())
                        .orElseThrow(() -> new EntityNotFoundException("Sub genre not found")));

        return book;
    }

    /**
     * DTO → Entity (UPDATE)
     */
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "totalReviews", ignore = true)
    public void updateBookFromDto(BookRequestDto dto, @MappingTarget Book book) {

        if (dto.getTitle() != null)
            book.setTitle(dto.getTitle());
        if (dto.getDescription() != null)
            book.setDescription(dto.getDescription());
        if (dto.getLanguage() != null)
            book.setLanguage(dto.getLanguage());
        if (dto.getAgeRangeMin() != null)
            book.setAgeRangeMin(dto.getAgeRangeMin());
        if (dto.getAgeRangeMax() != null)
            book.setAgeRangeMax(dto.getAgeRangeMax());
        if (dto.getPageCount() != null)
            book.setPageCount(dto.getPageCount());
        if (dto.getHasAudio() != null)
            book.setHasAudio(dto.getHasAudio());
        if (dto.getStatus() != null)
            book.setStatus(dto.getStatus());

        if (dto.getMainGenreId() != null) {
            book.setMainGenre(
                    mainGenreRepository.findById(dto.getMainGenreId())
                            .orElseThrow(() -> new EntityNotFoundException("Main genre not found")));
        }

        if (dto.getSubGenreId() != null) {
            book.setSubGenre(
                    subGenreRepository.findById(dto.getSubGenreId())
                            .orElseThrow(() -> new EntityNotFoundException("Sub genre not found")));
        }
    }
}
