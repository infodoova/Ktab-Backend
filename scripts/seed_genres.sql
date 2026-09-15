-- ============================================================================
-- SQL Seed Script: Comprehensive Literary & Academic Genres for Ktab
-- Fully idempotent with dual Arabic and English taxonomy
-- ============================================================================

SET client_encoding = 'UTF8';

-- 1. Helper function to upsert main genres cleanly
CREATE OR REPLACE FUNCTION ktab_upsert_main_genre(
    p_name_ar VARCHAR,
    p_name_en VARCHAR,
    p_description VARCHAR
) RETURNS BIGINT AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT col_id INTO v_id FROM tbl_main_genres 
    WHERE name_ar = p_name_ar OR name_en = p_name_en 
    LIMIT 1;

    IF v_id IS NULL THEN
        INSERT INTO tbl_main_genres (name_ar, name_en, description, active, created_at, updated_at, version)
        VALUES (p_name_ar, p_name_en, p_description, TRUE, NOW(), NOW(), 0)
        RETURNING col_id INTO v_id;
    ELSE
        UPDATE tbl_main_genres
        SET name_ar = p_name_ar,
            name_en = p_name_en,
            description = COALESCE(description, p_description),
            updated_at = NOW()
        WHERE col_id = v_id;
    END IF;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- 2. Helper function to upsert sub-genres cleanly
CREATE OR REPLACE FUNCTION ktab_upsert_sub_genre(
    p_main_id BIGINT,
    p_name_ar VARCHAR,
    p_name_en VARCHAR,
    p_description VARCHAR
) RETURNS BIGINT AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT col_id INTO v_id FROM tbl_sub_genres
    WHERE main_genre_id = p_main_id AND (name_ar = p_name_ar OR name_en = p_name_en)
    LIMIT 1;

    IF v_id IS NULL THEN
        INSERT INTO tbl_sub_genres (name_ar, name_en, description, active, main_genre_id, created_at, updated_at, version)
        VALUES (p_name_ar, p_name_en, p_description, TRUE, p_main_id, NOW(), NOW(), 0)
        RETURNING col_id INTO v_id;
    ELSE
        UPDATE tbl_sub_genres
        SET name_ar = p_name_ar,
            name_en = p_name_en,
            description = COALESCE(description, p_description),
            updated_at = NOW()
        WHERE col_id = v_id;
    END IF;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    v_fiction_id         BIGINT;
    v_non_fiction_id     BIGINT;
    v_politics_id        BIGINT;
    v_history_id         BIGINT;
    v_philosophy_id      BIGINT;
    v_religion_id        BIGINT;
    v_psychology_id      BIGINT;
    v_business_id        BIGINT;
    v_tech_id            BIGINT;
    v_literature_id      BIGINT;
    v_children_id        BIGINT;
BEGIN
    -- ------------------------------------------------------------------------
    -- 1. Fiction & Literature (خيال وروايات)
    -- ------------------------------------------------------------------------
    v_fiction_id := ktab_upsert_main_genre('خيال', 'Fiction', 'أعمال أدبية وروائية قائمة على الخيال والسرد القصصي');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'خيال علمي', 'Science Fiction', 'روايات وقصص تستند إلى التطورات العلمية والتكنولوجية المستقبلية');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'فانتازيا', 'Fantasy', 'أدب قائم على عوالم سحرية وخوارق وأساطير ومخلوقات خيالية');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'غموض وجريمة', 'Mystery & Crime', 'روايات بوليسية وألغاز وجرائم وتحقيقات مشوقة');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'خيال تاريخي', 'Historical Fiction', 'روايات تجري أحداثها في حقب تاريخية ماضية مع عناصر درامية متخيلة');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'رعب وتشويق', 'Horror & Suspense', 'أعمال تبث مشاعر الإثارة النفسية والرهبة والغموض');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'دراما اجتماعية', 'Social Drama', 'أعمال روائية تتناول قضايا المجتمع والأسرة والعلاقات الإنسانية المعاصرة');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'روايات رومانسية', 'Romance', 'أعمال تركز على العلاقات العاطفية والتجارب الإنسانية الوجدانية');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'مغامرات ورحلات', 'Adventure & Exploration', 'قصص قائمة على الاستكشاف والرحلات والمخاطر المثيرة');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'قصص قصيرة ونوفيلا', 'Short Stories & Novellas', 'مجموعات قصصية وسرديات مركزة ومكثفة');
    PERFORM ktab_upsert_sub_genre(v_fiction_id, 'أدب كلاسيكي عالمي', 'Classic Literature', 'روائع الأدب العربي والعالمي الخالدة عبر الأجيال');

    -- ------------------------------------------------------------------------
    -- 2. Non-Fiction (واقعي وغير روائي)
    -- ------------------------------------------------------------------------
    v_non_fiction_id := ktab_upsert_main_genre('واقعي', 'Non-Fiction', 'كتب معرفية توثيقية وتحليلية غير خيالية');
    PERFORM ktab_upsert_sub_genre(v_non_fiction_id, 'تاريخ عام', 'General History', 'دراسات وبحوث توثيقية للأحداث والشخصيات التاريخية');
    PERFORM ktab_upsert_sub_genre(v_non_fiction_id, 'سير وتراجم ذاتية', 'Biographies & Memoirs', 'قصص حياة شخصيات تاريخية وعامة ومذكرات ملهمة');
    PERFORM ktab_upsert_sub_genre(v_non_fiction_id, 'صحافة وتحقيقات', 'Journalism & Investigations', 'تقارير استقصائية وتحقيقات صحفية معمقة');
    PERFORM ktab_upsert_sub_genre(v_non_fiction_id, 'أدب الرحلات واليوميات', 'Travel & Diaries', 'توثيق الأسفار والمدن والمشاهدات الحية');
    PERFORM ktab_upsert_sub_genre(v_non_fiction_id, 'مقالات ودراسات نقدية', 'Essays & Critical Studies', 'مجموعات مقالات فكرية وأدبية ورؤى تحليلية');

    -- ------------------------------------------------------------------------
    -- 3. Politics & International Affairs (سياسة وشؤون دولية)
    -- ------------------------------------------------------------------------
    v_politics_id := ktab_upsert_main_genre('سياسة وشؤون دولية', 'Politics & International Affairs', 'دراسات سياسية وجيوسياسية وعلاقات دولية');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'علاقات دولية وجيوسياسة', 'International Relations & Geopolitics', 'تحليلات النظام الدولي وتوازنات القوى والصراعات العالمية');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'دراسات الشرق الأوسط', 'Middle East Studies', 'شؤون العالم العربي وقضايا الشرق الأوسط المعاصرة');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'القضية الفلسطينية والصراع العربي الإسرائيلي', 'Palestinian Cause & Conflict', 'أبحاث ووثائق وشهادات حول فلسطين وتاريخ الصراع وحقوق الشعب الفلسطيني');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'الفكر السياسي والأيديولوجيات', 'Political Thought & Ideologies', 'دراسة النظريات السياسية والديمقراطية والدولة والحرية');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'أمن قومي واستراتيجية عسكرية', 'National Security & Military Strategy', 'دراسات الدفاع والحروب والتحالفات الاستراتيجية');
    PERFORM ktab_upsert_sub_genre(v_politics_id, 'دبلوماسية وسياسات عامة', 'Diplomacy & Public Policy', 'فنون التفاوض وإدارة الأزمات وصنع السياسات الحكومية');

    -- ------------------------------------------------------------------------
    -- 4. History & Civilizations (تاريخ وحضارات)
    -- ------------------------------------------------------------------------
    v_history_id := ktab_upsert_main_genre('تاريخ وحضارات', 'History & Civilizations', 'دراسات التاريخ البشري والحضارات القديمة والحديثة');
    PERFORM ktab_upsert_sub_genre(v_history_id, 'تاريخ عربي وإسلامي', 'Arab & Islamic History', 'عصور الخلافة والدول الإسلامية والتراث التاريخي العربي');
    PERFORM ktab_upsert_sub_genre(v_history_id, 'حضارات قديمة وآثار', 'Ancient Civilizations & Archaeology', 'حضارات وادي الرافدين ومصر القديمة والشام وحضارات العالم');
    PERFORM ktab_upsert_sub_genre(v_history_id, 'تاريخ حديث ومعاصر', 'Modern & Contemporary History', 'القرنان التاسع عشر والعشرون وتحولات النظام العالمي');
    PERFORM ktab_upsert_sub_genre(v_history_id, 'تاريخ الحروب والثورات', 'Wars & Revolutions', 'تاريخ الثورات الكبرى والحروب العالمية وحركات التحرر');

    -- ------------------------------------------------------------------------
    -- 5. Philosophy & Thought (فكر وفلسفة)
    -- ------------------------------------------------------------------------
    v_philosophy_id := ktab_upsert_main_genre('فكر وفلسفة', 'Philosophy & Thought', 'أمهات الكتب الفلسفية ونظريات المعرفة والمنطق');
    PERFORM ktab_upsert_sub_genre(v_philosophy_id, 'فلسفة عامة ومنطق', 'General Philosophy & Logic', 'مباحث الوجود والمعرفة والمنطق الصوري والحديث');
    PERFORM ktab_upsert_sub_genre(v_philosophy_id, 'فكر عربي وإسلامي معاصر', 'Contemporary Arab & Islamic Thought', 'مشاريع النهضة والتحديث والتنوير في العالم العربي');
    PERFORM ktab_upsert_sub_genre(v_philosophy_id, 'فلسفة الأخلاق والسياسة', 'Ethics & Political Philosophy', 'العدالة والمواطنة وحقوق الإنسان ونظريات العقد الاجتماعي');
    PERFORM ktab_upsert_sub_genre(v_philosophy_id, 'علم الجمال والفلسفة الحديثة', 'Aesthetics & Modern Philosophy', 'الفينومينولوجيا والوجودية وما بعد الحداثة وفلسفة الفن');

    -- ------------------------------------------------------------------------
    -- 6. Religion & Islamic Studies (دين ودراسات إسلامية)
    -- ------------------------------------------------------------------------
    v_religion_id := ktab_upsert_main_genre('دين ودراسات إسلامية', 'Religion & Islamic Studies', 'علوم الشريعة والتراث الإسلامي والدراسات الدينية المقارنة');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'علوم القرآن والتفسير', 'Quranic Sciences & Exegesis', 'دراسات النص القرآني وأصول التفسير والإعجاز');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'الحديث والسنة النبوية', 'Hadith & Sunnah Studies', 'شروح الحديث الشريف ومناهج المحدثين');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'الفقه وأصول الشريعة ومقاصدها', 'Jurisprudence & Sharia Objectives', 'أحكام المعاملات والمقاصد الشرعية وفقه النوازل');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'سيرة نبوية وتاريخ الدعوة', 'Prophetic Biography', 'حياة النبي صلى الله عليه وسلم والجيل الأول');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'تصوف وسلوك وأخلاق', 'Sufism & Spiritual Ethics', 'تربية الروح والتزكية ومدارس التصوف الإسلامي');
    PERFORM ktab_upsert_sub_genre(v_religion_id, 'مقارنة أديان وتاريخ المعتقدات', 'Comparative Religion', 'دراسة الأديان السماوية والشرقية وتاريخ الأفكار الدينية');

    -- ------------------------------------------------------------------------
    -- 7. Psychology & Self-Development (علم نفس وتطوير الذات)
    -- ------------------------------------------------------------------------
    v_psychology_id := ktab_upsert_main_genre('علم نفس وتطوير الذات', 'Psychology & Self-Development', 'علوم السلوك البشري ومهارات النمو النفسي والشخصي');
    PERFORM ktab_upsert_sub_genre(v_psychology_id, 'تطوير الذات وبناء العادات', 'Self-Improvement & Habit Building', 'استراتيجيات النجاح الفردي وإدارة الذات والعادات الإيجابية');
    PERFORM ktab_upsert_sub_genre(v_psychology_id, 'علم النفس السلوكي والمعرفي', 'Behavioral & Cognitive Psychology', 'فهم العقل البشري والأنماط المعرفية والسلوك الإنساني');
    PERFORM ktab_upsert_sub_genre(v_psychology_id, 'ذكاء عاطفي وصحة نفسية', 'Emotional Intelligence & Mental Wellness', 'التعامل مع الضغوط والتوازن الوجداني والعلاقات الإنسانية');
    PERFORM ktab_upsert_sub_genre(v_psychology_id, 'إنتاجية وإدارة الوقت والطاقة', 'Productivity & Time Management', 'أدوات التنظيم والتركيز الفائق ومواجهة التسويف');
    PERFORM ktab_upsert_sub_genre(v_psychology_id, 'قيادة وتأثير وتواصل فعال', 'Leadership & Effective Communication', 'فنون الخطابة والتفاوض والإقناع وبناء فرق العمل');

    -- ------------------------------------------------------------------------
    -- 8. Business & Economics (إدارة واقتصاد)
    -- ------------------------------------------------------------------------
    v_business_id := ktab_upsert_main_genre('إدارة واقتصاد', 'Business & Economics', 'ريادة الأعمال والعلوم المالية والاقتصادية العالمية');
    PERFORM ktab_upsert_sub_genre(v_business_id, 'ريادة أعمال وتأسيس الشركات', 'Entrepreneurship & Startups', 'بناء النماذج الربحية والابتكار المؤسسي وتمويل المشاريع');
    PERFORM ktab_upsert_sub_genre(v_business_id, 'إدارة استراتيجية وتنظيمية', 'Strategic & Organizational Management', 'حوكمة الشركات والتخطيط الاستراتيجي وإدارة التغيير');
    PERFORM ktab_upsert_sub_genre(v_business_id, 'اقتصاد كلي وتنمية مستدامة', 'Macroeconomics & Development', 'النظريات الاقتصادية وأسواق المال والتنمية الاقتصادية');
    PERFORM ktab_upsert_sub_genre(v_business_id, 'تسويق وتجارة رقمية', 'Marketing & Digital Commerce', 'استراتيجيات العلامة التجارية والتسويق الحديث والبيع الرقمي');
    PERFORM ktab_upsert_sub_genre(v_business_id, 'مال واستثمار شخصي', 'Finance & Personal Investment', 'إدارة الثروات والأسهم والاستثمار الذكي');

    -- ------------------------------------------------------------------------
    -- 9. Science, Technology & AI (علوم وتكنولوجيا وذكاء اصطناعي)
    -- ------------------------------------------------------------------------
    v_tech_id := ktab_upsert_main_genre('علوم وتكنولوجيا', 'Science & Technology', 'العلوم الطبيعية والتطورات التقنية والذكاء الاصطناعي');
    PERFORM ktab_upsert_sub_genre(v_tech_id, 'ذكاء اصطناعي وتعلم الآلة', 'Artificial Intelligence & Machine Learning', 'النماذج التوليدية والخوارزميات وأثر الذكاء الاصطناعي على المجتمع');
    PERFORM ktab_upsert_sub_genre(v_tech_id, 'برمجة وهندسة البرمجيات', 'Programming & Software Engineering', 'لغات البرمجة وبناء الأنظمة وقواعد البيانات والحوسبة السحابية');
    PERFORM ktab_upsert_sub_genre(v_tech_id, 'علوم عامة وفيزياء وفلك', 'General Science, Physics & Astronomy', 'تبسيط العلوم واستكشاف الكون وقوانين الطبيعة');
    PERFORM ktab_upsert_sub_genre(v_tech_id, 'طب وصحة عامة وعلوم حيوية', 'Medicine, Health & Bioscience', 'التغذية والوقاية والعلوم الطبية الحديثة');
    PERFORM ktab_upsert_sub_genre(v_tech_id, 'أمن سيبراني وثقافة رقمية', 'Cybersecurity & Digital Culture', 'حماية البيانات والخصوصية الرقمية وتحديات الفضاء السيبراني');

    -- ------------------------------------------------------------------------
    -- 10. Poetry, Arts & Languages (شعر وفنون ولغات)
    -- ------------------------------------------------------------------------
    v_literature_id := ktab_upsert_main_genre('شعر وفنون ولغات', 'Poetry, Arts & Languages', 'الأدب والشعر والفنون البصرية والموسيقية واللغويات');
    PERFORM ktab_upsert_sub_genre(v_literature_id, 'شعر عربي قديم ومعاصر', 'Arabic Poetry (Classical & Modern)', 'دواوين فحول الشعراء وقصائد الشعر الحر والمعاصر');
    PERFORM ktab_upsert_sub_genre(v_literature_id, 'نقد أدبي وبلاغة ولغة عربية', 'Literary Criticism & Arabic Rhetoric', 'علوم المعاجم والنحو والصرف والنظريات الأدبية');
    PERFORM ktab_upsert_sub_genre(v_literature_id, 'فنون بصرية وتصميم وعمارة', 'Visual Arts, Design & Architecture', 'تاريخ الفنون والتشكيل والتصميم المعماري والحضري');
    PERFORM ktab_upsert_sub_genre(v_literature_id, 'سينما ومسرح وموسيقى', 'Cinema, Theater & Music', 'النقد السينمائي وتاريخ المسرح وفنون الأداء الموسيقي');

    -- ------------------------------------------------------------------------
    -- 11. Children & Young Adult (أطفال ويافعين)
    -- ------------------------------------------------------------------------
    v_children_id := ktab_upsert_main_genre('أطفال ويافعين', 'Children & Young Adult', 'أدب النشء والقصص التعليمية والتربوية واليافعين');
    PERFORM ktab_upsert_sub_genre(v_children_id, 'أدب أطفال وقصص مصورة', 'Children Literature & Picture Books', 'قصص تعليمية وخيالية مصورة للأطفال دون سن العاشرة');
    PERFORM ktab_upsert_sub_genre(v_children_id, 'روايات وقصص يافعين', 'Young Adult Fiction', 'روايات مشوقة تخاطب اهتمامات وتحديات سن المراهقة والشباب');
    PERFORM ktab_upsert_sub_genre(v_children_id, 'قصص تربوية وأخلاقية', 'Moral & Educational Stories', 'حكايات تعزز القيم والأخلاق الإيجابية وحب القراءة');
    PERFORM ktab_upsert_sub_genre(v_children_id, 'كوميكس ومانجا عربية', 'Comics & Graphic Novels', 'قصص مصورة ومغامرات مرئية تناسب الفئات العمرية الناشئة');

    RAISE NOTICE 'Genres and sub-genres seeded successfully!';
END $$;

-- 3. Cleanup helper functions to avoid schema clutter
DROP FUNCTION IF EXISTS ktab_upsert_sub_genre(BIGINT, VARCHAR, VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS ktab_upsert_main_genre(VARCHAR, VARCHAR, VARCHAR);
