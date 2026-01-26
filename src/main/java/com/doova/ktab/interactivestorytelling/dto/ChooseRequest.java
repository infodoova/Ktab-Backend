package com.doova.ktab.interactivestorytelling.dto;

import jakarta.validation.constraints.Pattern;

public record ChooseRequest(@Pattern(regexp="A|B|C|D") String choiceId) {}
