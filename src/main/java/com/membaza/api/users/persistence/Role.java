package com.membaza.api.users.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;
import java.util.Set;

/**
 * @author Emil Forslund
 * @since  1.0.0
 */
@Data
@Document(collection = "roles")
public final class Role {

    @Id
    private String id;
    private String name;
    private Set<String> privileges;

    @JsonIgnore
    public String getId() {
        return id;
    }
}