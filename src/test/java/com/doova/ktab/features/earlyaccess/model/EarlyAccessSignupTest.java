package com.doova.ktab.features.earlyaccess.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EarlyAccessSignupTest {

    private static EarlyAccessSignup signup(String email, String name, String phone) {
        EarlyAccessSignup s = new EarlyAccessSignup();
        s.setEmail(email);
        s.setFullName(name);
        s.setPhoneNumber(phone);
        return s;
    }

    @Test
    void theEmailIsStoredTrimmedAndLowerCaseSoTheUniqueIndexCannotBeDodged() {
        EarlyAccessSignup s = signup("  Sami.Ali@Example.COM ", "Sami Ali", null);

        s.normalize();

        assertThat(s.getEmail()).isEqualTo("sami.ali@example.com");
    }

    @Test
    void nameAndPhoneAreTrimmedAndABlankPhoneBecomesNull() {
        EarlyAccessSignup padded = signup("a@b.co", "  Sami Ali  ", " +961 70 123 456 ");
        EarlyAccessSignup blank = signup("a@b.co", "Sami Ali", "   ");

        padded.normalize();
        blank.normalize();

        assertThat(padded.getFullName()).isEqualTo("Sami Ali");
        assertThat(padded.getPhoneNumber()).isEqualTo("+961 70 123 456");
        assertThat(blank.getPhoneNumber()).isNull();
    }

    @Test
    void theOrganizationIsTrimmedBlankFieldsBecomeNullAndItsEmailIsLowerCase() {
        EarlyAccessSignup s = signup("lib@example.com", "Lina Haddad", null);
        EarlyAccessOrganization org = new EarlyAccessOrganization();
        org.setName("  Beirut Public Library ");
        org.setCity("  ");
        org.setWebsite(" https://library.example.org ");
        org.setEmail(" Info@Library.Example.org ");
        org.setPhone(" +961 1 234 567 ");
        s.setOrganization(org);

        s.normalize();

        assertThat(org.getName()).isEqualTo("Beirut Public Library");
        assertThat(org.getCity()).isNull();
        assertThat(org.getDescription()).isNull();
        assertThat(org.getWebsite()).isEqualTo("https://library.example.org");
        assertThat(org.getEmail()).isEqualTo("info@library.example.org");
        assertThat(org.getPhone()).isEqualTo("+961 1 234 567");
    }

    @Test
    void aSignupWithoutAnOrganizationStillNormalizes() {
        EarlyAccessSignup s = signup(" A@B.co ", " A ", null);

        s.normalize();

        assertThat(s.getOrganization()).isNull();
        assertThat(s.getEmail()).isEqualTo("a@b.co");
    }

    @Test
    void aNewSignupHasNoEarlyAccessYet() {
        assertThat(new EarlyAccessSignup().isEarlyAccess()).isFalse();
    }
}
