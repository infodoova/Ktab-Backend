package com.doova.ktab.features.storybook.support;

import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

public final class UserFixtures {

    private UserFixtures() {
    }

    public static User reader(TestEntityManager em, String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Parent");
        user.setPasswordDigest("not-a-real-hash");
        user.setRole("20"); // READER
        return em.persistAndFlush(user);
    }
}
