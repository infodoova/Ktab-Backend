package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.repository.ChildProfileRepository;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository repository;
    private final com.doova.ktab.features.storybook.repository.StorybookRepository books;
    private final com.doova.ktab.features.storybook.billing.StorybookCreditPort credits;

    @Transactional
    public ChildProfile createProfile(User owner, CreateChildProfileRequest r) {
        ChildProfile p = new ChildProfile();
        p.setOwner(owner);
        apply(p, r);
        return repository.save(p);
    }

    @Transactional
    public ChildProfileResponse create(User owner, CreateChildProfileRequest r) {
        return ChildProfileResponse.from(createProfile(owner, r));
    }

    @Transactional(readOnly = true)
    public List<ChildProfileResponse> list(User owner) {
        return repository.findByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(ChildProfileResponse::from).toList();
    }

    @Transactional
    public ChildProfileResponse update(User owner, Long id, CreateChildProfileRequest r) {
        ChildProfile p = requireOwned(owner, id);
        apply(p, r);
        return ChildProfileResponse.from(p);
    }

    /** Deleting a profile cascades to its books (FK ON DELETE CASCADE), but we refund held credits first. */
    @Transactional
    public void delete(User owner, Long id) {
        ChildProfile profile = requireOwned(owner, id);
        books.findByChildProfile_Id(profile.getId()).forEach(b -> credits.release(b.getId()));
        repository.delete(profile);
    }

    @Transactional(readOnly = true)
    public ChildProfile requireOwned(User owner, Long id) {
        return repository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_CHILD_NOT_FOUND));
    }

    private static void apply(ChildProfile p, CreateChildProfileRequest r) {
        p.setNameAr(r.nameAr().strip());
        p.setGender(r.gender());
        p.setAgeBand(r.ageBand());
        p.setAppearance(r.appearance());
    }
}
