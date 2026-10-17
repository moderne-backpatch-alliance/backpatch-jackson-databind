package com.fasterxml.jackson.databind.views;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

// CVE-2026-54518: named creator properties of a type that also has a
// @JsonUnwrapped property must honor the active view.
public class CreatorViewWithUnwrappedTest extends DatabindTestUtil
{
    static class PublicView { }
    static class AdminView extends PublicView { }

    public static class Address {
        public String city;
    }

    public static class Account {
        public final String name;
        public final String role;

        @JsonUnwrapped
        public Address address;

        @JsonCreator
        public Account(@JsonProperty("name") @JsonView(PublicView.class) String name,
                @JsonProperty("role") @JsonView(AdminView.class) String role) {
            this.name = name;
            this.role = role;
        }
    }

    @JsonDeserialize(builder = BuiltAccount.Builder.class)
    public static class BuiltAccount {
        public final String name;
        public final String role;
        public final Address address;

        BuiltAccount(String name, String role, Address address) {
            this.name = name;
            this.role = role;
            this.address = address;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            final String name;
            final String role;

            @JsonUnwrapped
            public Address address;

            @JsonCreator
            public Builder(@JsonProperty("name") @JsonView(PublicView.class) String name,
                    @JsonProperty("role") @JsonView(AdminView.class) String role) {
                this.name = name;
                this.role = role;
            }

            public BuiltAccount build() {
                return new BuiltAccount(name, role, address);
            }
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private final String JSON = a2q("{'name':'alice','role':'ADMIN','city':'Springfield'}");

    @Test
    public void beanCreatorPropertyHiddenByActiveView() throws Exception {
        Account result = MAPPER.readerWithView(PublicView.class)
                .forType(Account.class)
                .readValue(JSON);
        assertEquals("alice", result.name);
        assertNull(result.role);
        assertEquals("Springfield", result.address.city);
    }

    @Test
    public void beanCreatorPropertyWrittenInItsView() throws Exception {
        Account result = MAPPER.readerWithView(AdminView.class)
                .forType(Account.class)
                .readValue(JSON);
        assertEquals("ADMIN", result.role);
        assertEquals("Springfield", result.address.city);
    }

    @Test
    public void builderCreatorPropertyHiddenByActiveView() throws Exception {
        BuiltAccount result = MAPPER.readerWithView(PublicView.class)
                .forType(BuiltAccount.class)
                .readValue(JSON);
        assertEquals("alice", result.name);
        assertNull(result.role);
        assertEquals("Springfield", result.address.city);
    }

    @Test
    public void builderCreatorPropertyWrittenInItsView() throws Exception {
        BuiltAccount result = MAPPER.readerWithView(AdminView.class)
                .forType(BuiltAccount.class)
                .readValue(JSON);
        assertEquals("ADMIN", result.role);
        assertEquals("Springfield", result.address.city);
    }
}
