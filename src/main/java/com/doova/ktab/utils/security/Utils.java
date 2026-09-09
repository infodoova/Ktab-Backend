package com.doova.ktab.utils.security;

import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Utils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter COMPACT_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private Utils() {
    }

    public static Optional<User> getCurrentLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.ofNullable(userPrincipal.user());
        }

        return Optional.empty();
    }

    /**
     * Prefer SHA-256 for deterministic hashing. Do not use this for password hashing;
     * passwords should use an adaptive password encoder such as BCrypt/Argon2.
     */
    public static String sha256(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return digest("SHA-256", text);
    }

    /**
     * Kept only for backward compatibility. MD5 must not be used for security-sensitive hashing.
     */
    @Deprecated(forRemoval = false)
    public static String md5(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return digest("MD5", text);
    }

    private static String digest(String algorithm, String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(messageDigest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required hashing algorithm is unavailable: " + algorithm, e);
        }
    }

    public static String appendWithSpace(String content, String text) {
        String current = content == null ? "" : content.trim();
        String addition = text == null ? "" : text.trim();

        if (addition.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return addition;
        }
        return current + " " + addition;
    }

    /**
     * Returns the address supplied by the servlet container.
     *
     * Do not trust X-Forwarded-For or similar headers directly here because clients can spoof them.
     * If the application runs behind a trusted proxy, configure Spring/Tomcat forwarded-header
     * handling so request.getRemoteAddr() is rewritten only by trusted infrastructure.
     */
    public static String getClientIpAddressIfServletRequestExist(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String remoteAddr = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }

    /**
     * Sanitizes a user-provided value for use as a single filename/path segment.
     */
    public static String convertToFilename(String text) {
        if (text == null || text.isBlank()) {
            return "file";
        }

        String sanitized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\\\/\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("[^\\p{L}\\p{N}._-]", "")
                .replaceAll("_{2,}", "_")
                .replaceAll("^\\.+", "")
                .replaceAll("\\.+$", "");

        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "file";
        }

        return sanitized;
    }

    public static String getResourceContent(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }

        Resource resource = new ClassPathResource(resourceName);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(FileCopyUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read classpath resource: " + resourceName, e);
        }
    }

    public static List<Date> getDaysBetweenDates(Date startDate, Date endDate) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");

        if (!startDate.before(endDate)) {
            return List.of();
        }

        List<Date> dates = new ArrayList<>();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(startDate);

        while (calendar.getTime().before(endDate)) {
            dates.add(getStartOfDay(calendar.getTime()));
            calendar.add(Calendar.DATE, 1);
        }

        return dates;
    }

    public static Calendar calendarFromDate(Date date) {
        Objects.requireNonNull(date, "date must not be null");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public static Date decrementNDays(Date date, int n) {
        Calendar calendar = calendarFromDate(date);
        calendar.add(Calendar.DATE, -n);
        return calendar.getTime();
    }

    public static Date incrementNDays(Date date, int n) {
        Calendar calendar = calendarFromDate(date);
        calendar.add(Calendar.DATE, n);
        return calendar.getTime();
    }

    public static Date getStartOfDay(Date date) {
        Calendar calendar = calendarFromDate(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date getEndOfDay(Date date) {
        Calendar calendar = calendarFromDate(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    public static Date convertStringToDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static int daysBetween(Date d1, Date d2) {
        Objects.requireNonNull(d1, "d1 must not be null");
        Objects.requireNonNull(d2, "d2 must not be null");

        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = d1.toInstant().atZone(zone).toLocalDate();
        LocalDate second = d2.toInstant().atZone(zone).toLocalDate();
        return Math.toIntExact(ChronoUnit.DAYS.between(first, second));
    }

    public static String toHex(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateHexColor(String input) {
        return sha256(Objects.requireNonNull(input, "input must not be null"))
                .substring(0, 6);
    }

    public static boolean isEmpty(String text) {
        return text == null || text.isBlank();
    }

    public static BigInteger getRandomChars() {
        return new BigInteger(27, SECURE_RANDOM);
    }

    public static Integer generateInteger(int length) {
        if (length < 1 || length > 9) {
            throw new IllegalArgumentException("length must be between 1 and 9");
        }

        int lowerBound = (int) Math.pow(10, length - 1);
        int range = 9 * lowerBound;
        return lowerBound + SECURE_RANDOM.nextInt(range);
    }

    public static String generateString(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }

        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ALPHA_NUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHA_NUMERIC.length())));
        }
        return builder.toString();
    }

    public static Pageable generatePageable(
            int page,
            int size,
            List<String> sortBy,
            List<String> sortDirection
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than 0");
        }

        List<String> fields = sortBy == null ? List.of() : sortBy;
        List<String> directions = sortDirection == null ? List.of() : sortDirection;

        if (fields.size() != directions.size()) {
            throw new IllegalArgumentException(
                    "The number of sorting fields must match the number of sorting directions."
            );
        }

        List<Sort.Order> orders = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            String direction = directions.get(i);

            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("Sort field must not be blank");
            }
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("Sort direction must not be blank");
            }

            orders.add(new Sort.Order(
                    Sort.Direction.fromString(direction.trim()),
                    field.trim()
            ));
        }

        return orders.isEmpty()
                ? PageRequest.of(page, size)
                : PageRequest.of(page, size, Sort.by(orders));
    }

    public static BigDecimal safeConvertToBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static Integer safeConvertToInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate safeConvertToLocalDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateString.trim(), COMPACT_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean hasRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> roleName.equals(authority.getAuthority()));
    }
}
