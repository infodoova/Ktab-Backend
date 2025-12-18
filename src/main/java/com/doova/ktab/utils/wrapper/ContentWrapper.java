package com.doova.ktab.utils.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContentWrapper<T> {
    Iterable<T> content;

}