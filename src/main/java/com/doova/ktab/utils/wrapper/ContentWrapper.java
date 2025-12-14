package com.doova.ktab.utils.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class ContentWrapper<T> {
    Iterable<T> content = new ArrayList<>();

}