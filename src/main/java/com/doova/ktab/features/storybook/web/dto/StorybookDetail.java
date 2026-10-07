package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;

import java.util.List;

public record StorybookDetail(Long id, StorybookStatus status, String titleAr, String childNameAr,
                              LanguageVariety variety, TashkeelLevel tashkeelLevel, int pageCount,
                              String dedication, List<PageView> pages, String characterSheetUrl,
                              String failureReason, int lookRegenerationsLeft, int pageRegenerationsLeft,
                              String statusMessage) {

    public StorybookDetail(Long id, StorybookStatus status, String titleAr, String childNameAr,
                          LanguageVariety variety, TashkeelLevel tashkeelLevel, int pageCount,
                          String dedication, List<PageView> pages, String characterSheetUrl,
                          String failureReason, int lookRegenerationsLeft, int pageRegenerationsLeft) {
        this(id, status, titleAr, childNameAr, variety, tashkeelLevel, pageCount, dedication,
                pages, characterSheetUrl, failureReason, lookRegenerationsLeft, pageRegenerationsLeft,
                resolveStatusMessage(status));
    }

    public static String resolveStatusMessage(StorybookStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> "يجري تأليف وصياغة قصة طفلكم بعناية... (أرسلنا تفاصيل الرحلة إلى بريدك الإلكتروني).";
            case STORY_READY -> "اكتملت صياغة القصة وهي جاهزة لمراجعتكم واعتمادكم (أرسلنا إشعاراً ورابط المراجعة إلى بريدك الإلكتروني).";
            case CHARACTER_READY -> "لوحة ملامح شخصية طفلكم جاهزة للمعاينة والاعتماد (أرسلنا إشعاراً ورابط المعاينة إلى بريدك الإلكتروني).";
            case ILLUSTRATING -> "يجري رسم وتلوين صفحات القصة بالألوان المائية بدقة فائقة...";
            case QA -> "يجري فحص وتدقيق جودة صفحات القصة...";
            case RENDERING -> "يجري تجهيز وتجليد الكتاب النهائي للطباعة والمطالعة...";
            case READY -> "كتاب طفلكم مكتمل وجاهز للقراءة والتصفح والتحميل (تم إرسال روابط القراءة والتحميل إلى بريدك الإلكتروني).";
            case FAILED -> "حدث خطأ أثناء معالجة القصة، يمكنكم إعادة المحاولة.";
            case CANCELLED -> "تم إلغاء إعداد القصة.";
        };
    }
}
