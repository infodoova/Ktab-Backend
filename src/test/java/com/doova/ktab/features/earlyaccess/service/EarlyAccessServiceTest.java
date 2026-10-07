package com.doova.ktab.features.earlyaccess.service;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.features.earlyaccess.enums.EarlyAccessRoles;
import com.doova.ktab.features.earlyaccess.enums.Gender;
import com.doova.ktab.features.earlyaccess.model.EarlyAccessSignup;
import com.doova.ktab.features.earlyaccess.repository.EarlyAccessSignupRepository;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessOrganizationRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EarlyAccessServiceTest {

    private final EarlyAccessSignupRepository signups = mock(EarlyAccessSignupRepository.class);
    private final EarlyAccessService service = new EarlyAccessService(signups);

    private static EarlyAccessSignupRequest request(String email) {
        return request(email, UserRole.AUTHOR);
    }

    private static EarlyAccessSignupRequest request(String email, UserRole role) {
        EarlyAccessOrganizationRequest organization = role == UserRole.ADMIN_LIBRARIAN
                ? new EarlyAccessOrganizationRequest("Beirut Public Library", null, null, null, null, null, null, null) : null;
        return new EarlyAccessSignupRequest(email, "Sami Ali", role, organization, "+961 70 123 456", Gender.MALE, "premium");
    }

    @Test
    void anAdminLibrariansOrganizationIsStoredWithTheMeaningOfTblLibraryOrganizationsColumns() {
        when(signups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        EarlyAccessOrganizationRequest organization = new EarlyAccessOrganizationRequest("  Beirut Public Library ",
                "A city library", "Beirut", "Lebanon", "Hamra St 12", "https://library.example.org",
                "Info@Library.Example.org", "+961 1 234 567");

        EarlyAccessSignupResponse response = service.signUp(new EarlyAccessSignupRequest("lib@example.com", "Lina Haddad",
                UserRole.ADMIN_LIBRARIAN, organization, null, Gender.FEMALE, null));

        ArgumentCaptor<EarlyAccessSignup> saved = ArgumentCaptor.forClass(EarlyAccessSignup.class);
        verify(signups).saveAndFlush(saved.capture());
        var org = saved.getValue().getOrganization();
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN);
        assertThat(org.getName()).isEqualTo("  Beirut Public Library ");
        assertThat(org.getDescription()).isEqualTo("A city library");
        assertThat(org.getCity()).isEqualTo("Beirut");
        assertThat(org.getCountry()).isEqualTo("Lebanon");
        assertThat(org.getAddress()).isEqualTo("Hamra St 12");
        assertThat(org.getWebsite()).isEqualTo("https://library.example.org");
        assertThat(org.getEmail()).isEqualTo("Info@Library.Example.org");
        assertThat(org.getPhone()).isEqualTo("+961 1 234 567");
        assertThat(response.organizationName()).isNotBlank();
    }

    @Test
    void aReaderOrAuthorSignupHasNoOrganization() {
        when(signups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.signUp(request("r@example.com", UserRole.READER)).organizationName()).isNull();

        ArgumentCaptor<EarlyAccessSignup> saved = ArgumentCaptor.forClass(EarlyAccessSignup.class);
        verify(signups).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganization()).isNull();
    }

    @Test
    void aNewSignupIsSavedWithTheContactDetailsAndNoEarlyAccessYet() {
        when(signups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        EarlyAccessSignupResponse response = service.signUp(request("  Sami@Example.COM "));

        ArgumentCaptor<EarlyAccessSignup> saved = ArgumentCaptor.forClass(EarlyAccessSignup.class);
        verify(signups).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("sami@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Sami Ali");
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("+961 70 123 456");
        assertThat(saved.getValue().getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.AUTHOR);
        assertThat(response.role()).isEqualTo(UserRole.AUTHOR);
        assertThat(saved.getValue().getPlan()).isEqualTo("premium");
        assertThat(saved.getValue().isEarlyAccess()).isFalse();
        assertThat(response.email()).isEqualTo("sami@example.com");
        assertThat(response.earlyAccess()).isFalse();
    }

    @Test
    void eachOfTheThreeRolesIsStoredAsAsked() {
        when(signups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        for (UserRole role : EarlyAccessRoles.ALLOWED) {
            assertThat(service.signUp(request(role.name().toLowerCase() + "@example.com", role)).role()).isEqualTo(role);
        }
        assertThat(EarlyAccessRoles.ALLOWED).containsExactlyInAnyOrder(
                UserRole.READER, UserRole.AUTHOR, UserRole.ADMIN_LIBRARIAN);
    }

    @Test
    void anEmailThatIsAlreadyRegisteredIsAConflictWhateverItsCapitalization() {
        when(signups.existsByEmailIgnoreCase("sami@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signUp(request("SAMI@example.com")))
                .isInstanceOf(EarlyAccessConflictException.class);
        verify(signups, never()).saveAndFlush(any());
    }

    @Test
    void twoRequestsAtOnceStillEndAsAConflictForTheSecond() {
        when(signups.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_early_access_email"));

        assertThatThrownBy(() -> service.signUp(request("sami@example.com")))
                .isInstanceOf(EarlyAccessConflictException.class);
    }
}
