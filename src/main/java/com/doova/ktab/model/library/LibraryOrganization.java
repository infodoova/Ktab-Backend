package com.doova.ktab.model.library;

import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "tbl_library_organizations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_library_org_slug", columnNames = "col_slug")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = {"staff", "books"})
public class LibraryOrganization extends BaseEntity {

    @NotBlank(message = "{validation.library.name.required}")
    @Column(name = "col_name", nullable = false)
    private String name;

    @NotBlank(message = "{validation.library.slug.required}")
    @Column(name = "col_slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "col_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "col_logo_storage_path")
    private String logoStoragePath;

    @Column(name = "col_city")
    private String city;

    @Column(name = "col_country")
    private String country;

    @Column(name = "col_address")
    private String address;

    @Column(name = "col_website")
    private String website;

    @Column(name = "col_email")
    private String email;

    @Column(name = "col_phone")
    private String phone;

    @Column(name = "col_status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "libraryOrganization", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> staff = new HashSet<>();

    @OneToMany(mappedBy = "libraryOrganization", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Book> books = new HashSet<>();
}
