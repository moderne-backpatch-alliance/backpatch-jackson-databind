package com.fasterxml.jackson.databind.views;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static com.fasterxml.jackson.databind.BaseMapTest.newJsonMapper;
import static com.fasterxml.jackson.databind.BaseTest.a2q;

import static org.junit.jupiter.api.Assertions.*;

// [databind#5969]: `@JsonView` must be honored for regular properties buffered
// while collecting creator properties of a POJO that also has `@JsonUnwrapped`
// properties (the "deserializeUsingPropertyBasedWithUnwrapped()" paths).
public class BufferedPropsViewWithUnwrapped5969Test
{
    static class PublicV { }
    static class AdminV extends PublicV { }

    static class Extra {
        public String note;
    }

    static class Bean {
        public String name, city;
        protected String password;

        @JsonUnwrapped
        public Extra extra;

        @JsonCreator
        public Bean(@JsonProperty("name") String n, @JsonProperty("city") String c) {
            name = n; city = c;
        }

        @JsonView(AdminV.class)
        public void setPassword(String p) { password = p; }
    }

    @JsonDeserialize(builder = User.Builder.class)
    static class User {
        public final String name, city, password;
        public final Extra extra;

        User(String n, String c, String p, Extra e) {
            name = n; city = c; password = p; extra = e;
        }

        @JsonPOJOBuilder(withPrefix = "")
        static class Builder {
            String name, city, password;
            Extra extra;

            @JsonCreator
            public Builder(@JsonProperty("name") String n, @JsonProperty("city") String c) {
                name = n; city = c;
            }
            @JsonView(AdminV.class) public Builder password(String p) { password = p; return this; }
            @JsonUnwrapped public Builder extra(Extra e) { extra = e; return this; }
            public User build() { return new User(name, city, password, extra); }
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private final static String JSON = a2q(
            "{'name':'alice','password':'BYPASS','city':'NY','note':'n'}");

    @Test
    public void beanViewHonoredBetweenCreatorProps() throws Exception {
        Bean b = MAPPER.readerFor(Bean.class).withView(PublicV.class).readValue(JSON);
        assertEquals("alice", b.name);
        assertEquals("NY", b.city);
        assertNull(b.password, "password should be hidden by active view");
    }

    @Test
    public void beanAdminViewStillWrites() throws Exception {
        Bean b = MAPPER.readerFor(Bean.class).withView(AdminV.class).readValue(JSON);
        assertEquals("BYPASS", b.password);
        assertEquals("n", b.extra.note);
    }

    @Test
    public void builderViewHonoredBetweenCreatorProps() throws Exception {
        User u = MAPPER.readerFor(User.class).withView(PublicV.class).readValue(JSON);
        assertEquals("alice", u.name);
        assertEquals("NY", u.city);
        assertNull(u.password, "password should be hidden by active view");
    }

    @Test
    public void builderAdminViewStillWrites() throws Exception {
        User u = MAPPER.readerFor(User.class).withView(AdminV.class).readValue(JSON);
        assertEquals("BYPASS", u.password);
        assertEquals("n", u.extra.note);
    }
}
