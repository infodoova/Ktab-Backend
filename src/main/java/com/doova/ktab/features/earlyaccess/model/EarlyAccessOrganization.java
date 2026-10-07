package com.doova.ktab.features.earlyaccess.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * The library organization an admin librarian signs up with. Each field means what the same-named column of
 * tbl_library_organizations means (name, description, city, country, address, website, email, phone), so the organization
 * can be created from it as it is when the signup becomes active. No slug (generated then) and no logo or status.
 */
@Embeddable
@Getter
@Setter
public class EarlyAccessOrganization {

    @Column(name = "col_org_name", length = 255)
    private String name;

    @Column(name = "col_org_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "col_org_city", length = 100)
    private String city;

    @Column(name = "col_org_country", length = 100)
    private String country;

    @Column(name = "col_org_address", length = 255)
    private String address;

    @Column(name = "col_org_website", length = 255)
    private String website;

    @Column(name = "col_org_email", length = 255)
    private String email;

    @Column(name = "col_org_phone", length = 50)
    private String phone;

    /** Trims every field and turns a blank one into null. */
    void normalize() {
        name = clean(name);
        description = clean(description);
        city = clean(city);
        country = clean(country);
        address = clean(address);
        website = clean(website);
        email = email == null ? null : clean(email.toLowerCase(java.util.Locale.ROOT));
        phone = clean(phone);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
