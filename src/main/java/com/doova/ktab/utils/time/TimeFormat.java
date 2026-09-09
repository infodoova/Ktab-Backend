package com.doova.ktab.utils.time;

public final class TimeFormat {

    private TimeFormat() {
    }

    public static final String DD_MM_YYYY_HH_MM_SS_Z = "dd-MM-yyyy HH:mm:ss Z";

    public static final String MM_DD_YYYY_HH_MM_SS_UTC = "MM/dd/yyyy HH:mm:ss 'UTC'";

    /**
     * @deprecated The constant name does not match its actual MM/dd/yyyy pattern.
     */
    @Deprecated(forRemoval = false)
    public static final String DD_MM_YYYY_HH_MM_SS_UTC = MM_DD_YYYY_HH_MM_SS_UTC;

    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    /**
     * @deprecated Use {@link #YYYY_MM_DD}; the old constant name was misleading.
     */
    @Deprecated(forRemoval = false)
    public static final String DD_MM_YYYY = YYYY_MM_DD;

    public static final String DATETIME_WITH_OFFSET_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final String RESPONSE_DATE_FORMAT = "MM/dd/yyyy";
    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    public static final String HH_MM = "HH:mm";
}
