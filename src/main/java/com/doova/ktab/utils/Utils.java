package com.doova.doovafeeds.utils;


import com.doova.doovafeeds.model.User;
import com.doova.doovafeeds.security.UserPrincipal;
import io.micrometer.common.util.StringUtils;
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Utils {

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    };

    public static Optional<User> getCurrentLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserPrincipal) {
                User user = ((UserPrincipal) principal).getUser();
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    public static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashInBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashInBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
        }
        return null;
    }

    public static String appendWithSpace(String content, String text) {
        if (!text.equals("")) {
            if (content.equals("")) {
                content = text;
            } else {
                content += " " + text;
            }
        }
        return content;
    }

    public static String getClientIpAddressIfServletRequestExist(HttpServletRequest request) {
        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (ipList != null && ipList.length() != 0 && !"unknown".equalsIgnoreCase(ipList)) {
                String ip = ipList.split(",")[0];
                return ip;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr.equals("0:0:0:0:0:0:0:1")) {
            remoteAddr = "127.0.0.1";
        }
        return remoteAddr;
    }


    public static String convertToFilename(String text) {
        return text.replaceAll("[^a-zA-Z0-9_.]", "");
    }

    public static String getResourceContent(String resourceName) {
        String content = "";

        try {
            Resource resource = new ClassPathResource(resourceName);
            InputStream inputStream = resource.getInputStream();
            byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
            content = new String(bdata, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }


    public static List<Date> getDaysBetweenDates(Date startdate, Date enddate) {

        List<Date> dates = new ArrayList<Date>();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(startdate);

        while (calendar.getTime().before(enddate)) {
            Date result = calendar.getTime();
            dates.add(getStartOfDay(result));
            calendar.add(Calendar.DATE, 1);
        }
        return dates;
    }

    public static Calendar calendarFromDate(Date date) {
        try {
            if (date == null) {
                return null;
            }
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            return c;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Date decrementNDays(Date date, int n) {
        Calendar cal = calendarFromDate(date);
        cal.add(Calendar.DATE, -n);
        return cal.getTime();
    }

    public static Date incrementNDays(Date date, int n) {
        Calendar cal = calendarFromDate(date);
        cal.add(Calendar.DATE, n);
        return cal.getTime();
    }

    public static Date getStartOfDay(Date date) {
        Calendar cal = calendarFromDate(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date getEndOfDay(Date date) {
        Calendar cal = calendarFromDate(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    public static Date convertStringToDate(String dateStr) {
        Date date;
        try {
            DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
            date = df.parse(dateStr);
            return date;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int daysBetween(Date d1, Date d2) {
        return (int) ((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    }

    public static String toHex(String arg) {
        return String.format("%040x", new BigInteger(1, arg.getBytes()));
    }

    public static String generateHexColor(String input) {
        String hex = toHex(input);
        return hex.substring(hex.length() - 6);
    }

    public static boolean isEmpty(String text) {
        return text == null || "".equals(text.trim());
    }

    public static BigInteger getRandomChars() {
        Random rand = new Random();
        BigInteger result = new BigInteger(27, rand); // (2^4-1) = 15 is the maximum value
        return result;
    }

    public static Integer generateInteger(int length) {
        Random rnd = new Random();
        return (int) Math.pow(10, length - 1) + rnd.nextInt((int) Math.pow(9, length - 1));
    }

    public static String generateString(int count) {
        String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        StringBuilder sb = new StringBuilder();
        while (count-- != 0) {
            int charactar = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            sb.append(ALPHA_NUMERIC_STRING.charAt(charactar));
        }
        return sb.toString();
    }

    public static Pageable generatePageable(int page, int size, List<String> sortBy, List<String> sortDirection){
        if (sortBy == null) {
            sortBy = new ArrayList<>();
        }
        if (sortDirection == null) {
            sortDirection = new ArrayList<>();
        }
        if (sortBy.size() != sortDirection.size()) {
            throw new IllegalArgumentException("The number of sorting fields must match the number of sorting directions.");
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < sortBy.size(); i++) {
            Sort.Direction direction = sortDirection.get(i).equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            orders.add(new Sort.Order(direction, sortBy.get(i)));
        }
        return         PageRequest.of(page, size, Sort.by(orders));

    }

//    public static <T> T filterRecordFields(T record, List<String> userRoles) {
//        for (Field field : record.getClass().getDeclaredFields()) {
//            if (field.isAnnotationPresent(SecuredField.class)) {
//                SecuredField securedField = field.getAnnotation(SecuredField.class);
//                // Check if the user has access to the field
//                boolean hasAccess = userRoles.stream().anyMatch(role ->
//                        List.of(securedField.allowedRoles()).contains(role)
//                );
//                if (!hasAccess) {
//                    try {
//                        field.setAccessible(true);
//                        field.set(record, null); // Set the field to null if access is denied
//                    } catch (IllegalAccessException e) {
//                        e.printStackTrace(); // Handle the exception according to your needs
//                    }
//                }
//            }
//        }
//        return record;
//    }


    public static BigDecimal safeConvertToBigDecimal(String value) {
        if (value == null ||  StringUtils.isBlank(value)) {
            return BigDecimal.ZERO; // Or return null, depending on your needs
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            e.printStackTrace(); // Log the error or handle it as needed
            return BigDecimal.ZERO; // Or handle accordingly
        }
    }

    // Utility method for safely converting to Integer
    public static Integer safeConvertToInteger(String value) {
        if (value == null || StringUtils.isBlank(value) ) {
            return null; // Or return 0, depending on your needs
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            e.printStackTrace(); // Log the error or handle it as needed
            return null; // Or handle accordingly
        }
    }

    public static  LocalDate safeConvertToLocalDate(String dateString) {
        if (dateString == null || StringUtils.isBlank(dateString)) {
            return null; // Or return LocalDate.MIN, depending on your needs
        }
        try {
            // Specify the expected date format, e.g., "yyyy-MM-dd"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            return LocalDate.parse(dateString.trim(), formatter);
        } catch (DateTimeParseException e) {
            e.printStackTrace(); // Log the error or handle it as needed
            return null; // Or handle accordingly, e.g., return LocalDate.MIN
        }
    }
}
