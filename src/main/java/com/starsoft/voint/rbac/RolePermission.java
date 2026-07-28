package com.starsoft.voint.rbac;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One cell of the matrix: this role may perform this action on this resource. */
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Column(name = "role_id")
    private UUID roleId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission.Resource resource;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission.Action action;

    /** Composite key. Only grants are stored - a missing row is a denial. */
    public static class Key implements Serializable {
        private UUID roleId;
        private Permission.Resource resource;
        private Permission.Action action;

        public Key() {
        }

        public Key(UUID roleId, Permission.Resource resource, Permission.Action action) {
            this.roleId = roleId;
            this.resource = resource;
            this.action = action;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(roleId, key.roleId)
                    && resource == key.resource
                    && action == key.action;
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, resource, action);
        }
    }
}
