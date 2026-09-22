package com.doova.ktab.service.book;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BookStatusTransitionTest {

    @Test
    @DisplayName("assertAllowed_draftToUnderReview_doesNotThrow")
    void assertAllowed_draftToUnderReview_doesNotThrow() {
        assertDoesNotThrow(() -> BookStatusTransition.assertAllowed(BookStatus.DRAFT, BookStatus.UNDER_REVIEW));
    }

    @Test
    @DisplayName("assertAllowed_draftToPublished_doesNotThrow")
    void assertAllowed_draftToPublished_doesNotThrow() {
        assertDoesNotThrow(() -> BookStatusTransition.assertAllowed(BookStatus.DRAFT, BookStatus.PUBLISHED));
    }

    @Test
    @DisplayName("assertAllowed_underReviewToDraft_doesNotThrow")
    void assertAllowed_underReviewToDraft_doesNotThrow() {
        assertDoesNotThrow(() -> BookStatusTransition.assertAllowed(BookStatus.UNDER_REVIEW, BookStatus.DRAFT));
    }

    @Test
    @DisplayName("assertAllowed_underReviewToPublished_doesNotThrow")
    void assertAllowed_underReviewToPublished_doesNotThrow() {
        assertDoesNotThrow(() -> BookStatusTransition.assertAllowed(BookStatus.UNDER_REVIEW, BookStatus.PUBLISHED));
    }

    @Test
    @DisplayName("assertAllowed_sameStatus_throwsBadRequestException")
    void assertAllowed_sameStatus_throwsBadRequestException() {
        assertThatThrownBy(() -> BookStatusTransition.assertAllowed(BookStatus.DRAFT, BookStatus.DRAFT))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION));

        assertThatThrownBy(() -> BookStatusTransition.assertAllowed(BookStatus.UNDER_REVIEW, BookStatus.UNDER_REVIEW))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION));

        assertThatThrownBy(() -> BookStatusTransition.assertAllowed(BookStatus.PUBLISHED, BookStatus.PUBLISHED))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("assertAllowed_publishedToDraft_throwsBadRequestException")
    void assertAllowed_publishedToDraft_throwsBadRequestException() {
        assertThatThrownBy(() -> BookStatusTransition.assertAllowed(BookStatus.PUBLISHED, BookStatus.DRAFT))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("assertAllowed_publishedToUnderReview_throwsBadRequestException")
    void assertAllowed_publishedToUnderReview_throwsBadRequestException() {
        assertThatThrownBy(() -> BookStatusTransition.assertAllowed(BookStatus.PUBLISHED, BookStatus.UNDER_REVIEW))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION));
    }
}
