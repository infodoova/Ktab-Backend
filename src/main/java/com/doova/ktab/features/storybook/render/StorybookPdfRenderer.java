package com.doova.ktab.features.storybook.render;

public interface StorybookPdfRenderer {
    byte[] renderHtml(String htmlContent);
}
