package com.doova.ktab.dto.book;

import com.doova.ktab.model.book.Book;

public record BookScore(Book book, double score) {

    // Getter for the book
    public Book getBook() {
        return book;
    }

    // Getter for the score
    public double getScore() {
        return score;
    }
}
