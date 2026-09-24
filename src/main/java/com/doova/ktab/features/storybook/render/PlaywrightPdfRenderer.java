package com.doova.ktab.features.storybook.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlaywrightPdfRenderer implements StorybookPdfRenderer {

    @Override
    public byte[] renderHtml(String htmlContent) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(true);
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                Page page = browser.newPage();
                page.setContent(htmlContent, new Page.SetContentOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                Page.PdfOptions pdfOptions = new Page.PdfOptions()
                        .setWidth("210mm")
                        .setHeight("210mm")
                        .setPrintBackground(true)
                        .setMargin(new Margin().setTop("0").setBottom("0").setLeft("0").setRight("0"));
                return page.pdf(pdfOptions);
            }
        } catch (Exception e) {
            log.error("Failed to render storybook PDF via Playwright", e);
            throw new IllegalStateException("Failed to render storybook PDF: " + e.getMessage(), e);
        }
    }
}
