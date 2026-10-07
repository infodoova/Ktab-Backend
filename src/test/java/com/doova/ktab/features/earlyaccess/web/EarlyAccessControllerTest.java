package com.doova.ktab.features.earlyaccess.web;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.features.earlyaccess.service.EarlyAccessService;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EarlyAccessControllerTest {

    private final EarlyAccessService service = mock(EarlyAccessService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EarlyAccessController(service, mock(MessageSource.class))).build();
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mvc.perform(post("/public/early-access").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static final String ORGANIZATION = "\"organization\":{\"name\":\"Beirut Public Library\",\"city\":\"Beirut\","
            + "\"country\":\"Lebanon\",\"website\":\"https://library.example.org\",\"email\":\"info@library.example.org\","
            + "\"phone\":\"+961 1 234 567\"}";

    @Test
    void anAdminLibrarianSignsUpWithTheOrganizationTheyRun() throws Exception {
        when(service.signUp(any())).thenReturn(new EarlyAccessSignupResponse("lib@example.com", "Lina", UserRole.ADMIN_LIBRARIAN,
                "Beirut Public Library", null, false));

        send("{\"email\":\"lib@example.com\",\"fullName\":\"Lina\",\"role\":\"ADMIN_LIBRARIAN\"," + ORGANIZATION + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ADMIN_LIBRARIAN"))
                .andExpect(jsonPath("$.data.organizationName").value("Beirut Public Library"));
    }

    @Test
    void anAdminLibrarianMustGiveAValidOrganizationAndNobodyElseMay() throws Exception {
        // no organization at all, or one without a name
        send("{\"email\":\"l@b.co\",\"fullName\":\"L\",\"role\":\"ADMIN_LIBRARIAN\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"l@b.co\",\"fullName\":\"L\",\"role\":\"ADMIN_LIBRARIAN\",\"organization\":{\"city\":\"Beirut\"}}")
                .andExpect(status().isBadRequest());
        send("{\"email\":\"l@b.co\",\"fullName\":\"L\",\"role\":\"ADMIN_LIBRARIAN\",\"organization\":{\"name\":\"  \"}}")
                .andExpect(status().isBadRequest());
        // an organization on a reader or an author
        send("{\"email\":\"r@b.co\",\"fullName\":\"R\",\"role\":\"READER\"," + ORGANIZATION + "}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"AUTHOR\"," + ORGANIZATION + "}").andExpect(status().isBadRequest());

        verify(service, never()).signUp(any(EarlyAccessSignupRequest.class));
    }

    @Test
    void theOrganizationFollowsTheLimitsAndFormatsOfTblLibraryOrganizations() throws Exception {
        String tooLong = "x".repeat(256);
        String start = "{\"email\":\"l@b.co\",\"fullName\":\"L\",\"role\":\"ADMIN_LIBRARIAN\",\"organization\":";

        send(start + "{\"name\":\"" + tooLong + "\"}}").andExpect(status().isBadRequest());
        send(start + "{\"name\":\"Lib\",\"city\":\"" + "c".repeat(101) + "\"}}").andExpect(status().isBadRequest());
        send(start + "{\"name\":\"Lib\",\"address\":\"" + tooLong + "\"}}").andExpect(status().isBadRequest());
        send(start + "{\"name\":\"Lib\",\"website\":\"library.example.org\"}}").andExpect(status().isBadRequest());
        send(start + "{\"name\":\"Lib\",\"email\":\"not-an-email\"}}").andExpect(status().isBadRequest());
        send(start + "{\"name\":\"Lib\",\"phone\":\"call me\"}}").andExpect(status().isBadRequest());

        verify(service, never()).signUp(any(EarlyAccessSignupRequest.class));
    }

    @Test
    void aValidSignupAnswers201WithTheRegisteredDetails() throws Exception {
        when(service.signUp(any())).thenReturn(new EarlyAccessSignupResponse("sami@example.com", "Sami Ali", UserRole.AUTHOR, null, "premium", false));

        send("{\"email\":\"sami@example.com\",\"fullName\":\"Sami Ali\",\"role\":\"AUTHOR\",\"phoneNumber\":\"+961 70 123 456\","
                + "\"gender\":\"MALE\",\"plan\":\"premium\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("sami@example.com"))
                .andExpect(jsonPath("$.data.role").value("AUTHOR"))
                .andExpect(jsonPath("$.data.earlyAccess").value(false));
    }

    @Test
    void onlyTheEmailNameAndRoleAreRequired() throws Exception {
        when(service.signUp(any())).thenReturn(new EarlyAccessSignupResponse("a@b.co", "A", UserRole.READER, null, null, false));

        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"READER\"}").andExpect(status().isCreated());
    }

    @Test
    void theRoleMustBeReaderAuthorOrAdminLibrarian() throws Exception {
        when(service.signUp(any())).thenReturn(new EarlyAccessSignupResponse("a@b.co", "A", UserRole.ADMIN_LIBRARIAN, "Lib", null, false));

        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"ADMIN_LIBRARIAN\",\"organization\":{\"name\":\"Lib\"}}")
                .andExpect(status().isCreated());

        send("{\"email\":\"a@b.co\",\"fullName\":\"A\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"LIBRARIAN\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"PUBLISHER\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aMissingOrMalformedFieldIsRejectedBeforeTheServiceIsCalled() throws Exception {
        send("{\"fullName\":\"Sami\",\"role\":\"READER\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"not-an-email\",\"fullName\":\"Sami\",\"role\":\"READER\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"  \",\"role\":\"READER\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"Sami\",\"role\":\"READER\",\"phoneNumber\":\"call me\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"Sami\",\"role\":\"READER\",\"plan\":\"a plan; drop table\"}").andExpect(status().isBadRequest());
        send("{\"email\":\"a@b.co\",\"fullName\":\"Sami\",\"role\":\"READER\",\"gender\":\"OTHER\"}").andExpect(status().isBadRequest());

        verify(service, never()).signUp(any(EarlyAccessSignupRequest.class));
    }

    @Test
    void aVisitorCannotGrantThemselvesEarlyAccess() throws Exception {
        when(service.signUp(any())).thenReturn(new EarlyAccessSignupResponse("a@b.co", "A", UserRole.READER, null, null, false));

        send("{\"email\":\"a@b.co\",\"fullName\":\"A\",\"role\":\"READER\",\"earlyAccess\":true}").andExpect(status().isCreated());

        // the request type has no such field, so the flag never reaches the service
        verify(service).signUp(new EarlyAccessSignupRequest("a@b.co", "A", UserRole.READER, null, null, null, null));
    }
}
