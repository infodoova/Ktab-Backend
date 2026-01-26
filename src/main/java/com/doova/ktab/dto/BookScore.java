package com.doova.ktab.dto;

import com.doova.ktab.model.book.Book;
import lombok.Getter;

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
