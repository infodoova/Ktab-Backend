package com.doova.ktab.model.base;

import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    static class AnotherTestEntity extends BaseEntity {
    }

    static class SubclassEntity extends TestEntity {
    }

    @Test
    @DisplayName("Entities with same ID should be equal and have identical hashCode")
    void sameIdEqualsAndHashCode() {
        TestEntity e1 = new TestEntity();
        e1.setId(42L);

        TestEntity e2 = new TestEntity();
        e2.setId(42L);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    @DisplayName("Entities with different ID should not be equal")
    void differentIdNotEqual() {
        TestEntity e1 = new TestEntity();
        e1.setId(1L);

        TestEntity e2 = new TestEntity();
        e2.setId(2L);

        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("Entities with null ID should not be equal to another entity with null ID")
    void nullIdNotEqual() {
        TestEntity e1 = new TestEntity();
        TestEntity e2 = new TestEntity();

        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("Entity should equal itself even with null ID")
    void sameInstanceEqual() {
        TestEntity e1 = new TestEntity();
        assertEquals(e1, e1);
    }

    @Test
    @DisplayName("Entity should not equal null or different types")
    void notEqualToNullOrDifferentType() {
        TestEntity e1 = new TestEntity();
        e1.setId(10L);

        AnotherTestEntity e2 = new AnotherTestEntity();
        e2.setId(10L);

        assertNotEquals(e1, null);
        assertNotEquals(e1, "some string");
        assertNotEquals(e1, e2);
    }

    static class FakeProxyEntity extends TestEntity implements org.hibernate.proxy.HibernateProxy {
        private final org.hibernate.proxy.LazyInitializer li;

        FakeProxyEntity(Long id, org.hibernate.proxy.LazyInitializer li) {
            setId(id);
            this.li = li;
        }

        @Override
        public org.hibernate.proxy.LazyInitializer getHibernateLazyInitializer() {
            return li;
        }

        @Override
        public Object writeReplace() {
            return this;
        }
    }

    @Test
    @DisplayName("Distinct non-proxy subclass should not be equal to parent entity")
    void nonProxySubclassNotEqual() {
        TestEntity parent = new TestEntity();
        parent.setId(99L);

        SubclassEntity child = new SubclassEntity();
        child.setId(99L);

        assertNotEquals(parent, child);
    }

    @Test
    @DisplayName("HibernateProxy with matching persistent class and ID should be equal (proxy-safe)")
    void hibernateProxyEqual() {
        TestEntity parent = new TestEntity();
        parent.setId(99L);

        org.hibernate.proxy.LazyInitializer li = org.mockito.Mockito.mock(org.hibernate.proxy.LazyInitializer.class);
        org.mockito.Mockito.doReturn(TestEntity.class).when(li).getPersistentClass();

        FakeProxyEntity proxy = new FakeProxyEntity(99L, li);

        assertEquals(parent, proxy);
        assertEquals(proxy, parent);
        assertEquals(parent.hashCode(), proxy.hashCode());
    }
}
