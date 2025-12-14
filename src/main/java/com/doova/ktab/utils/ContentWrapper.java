package com.doova.doovafeeds.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class ContentWrapper<T> {
    Iterable<T> content = new ArrayList<>();

}