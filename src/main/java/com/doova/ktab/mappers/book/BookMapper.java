package com.doova.ktab.mappers.book;


import com.doova.ktab.api.dto.request.BookRequestDto;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.user.UserService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Mapper interface for converting between Book Entity and Book DTOs using MapStruct.
 * Defined as an abstract class to allow dependency injection (UserService) for
 * resolving the foreign key (User author) during DTO-to-Entity mapping.
 */
@Mapper(componentModel = "spring")
public abstract class BookMapper {

    /**
     * Converts a Book entity to a BookResponseDto.
     */
    public abstract BookResponseDto toResponseDto(Book book);

    /**
     * Converts a list of Book entities to a list of BookResponseDtos.
     */
    public abstract List<BookResponseDto> toResponseDto(List<Book> books);

    /**
     * Converts a BookRequestDto to a Book entity for creation.
     * The 'authorId' in the DTO is automatically mapped to the 'author' entity
     * using the custom 'mapAuthorIdToUser' method.
     */
    @Mapping(target = "author", ignore = true)
//    @Mapping(target = "reviews", ignore = true)        // Ignore collections
//    @Mapping(target = "libraryEntries", ignore = true) // Ignore collections
    public abstract Book toEntity(BookRequestDto dto);

    /**
     * Updates an existing Book entity from a BookRequestDto.
     *
     * @param dto  The source DTO.
     * @param book The target existing entity.
     */
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "id", ignore = true) // Never overwrite the ID on update
    @Mapping(target = "audit.createdAt", ignore = true) // Never overwrite audit fields
    @Mapping(target = "audit.updatedAt", ignore = true)
    @Mapping(target = "averageRating", ignore = true) // Ratings are calculated, not updated manually
    @Mapping(target = "totalReviews", ignore = true)
    @Mapping(target = "hasAudio", ignore = true) // Handled separately
    @Mapping(target = "pageCount", ignore = true) // Handled separately
    public abstract void updateBookFromDto(BookRequestDto dto, @MappingTarget Book book);

}