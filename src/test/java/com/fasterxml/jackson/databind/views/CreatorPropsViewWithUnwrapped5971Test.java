package com.fasterxml.jackson.databind.views;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static com.fasterxml.jackson.databind.BaseMapTest.newJsonMapper;
import static com.fasterxml.jackson.databind.BaseTest.a2q;

import static org.junit.jupiter.api.Assertions.*;

// [databind#5971]: `@JsonView` must be honored for creator properties of a POJO
// that also has `@JsonUnwrapped` properties (the
// "deserializeUsingPropertyBasedWithUnwrapped()" paths), same as for POJOs without.
public class CreatorPropsViewWithUnwrapped5971Test
{
    static class PublicV { }
    static class AdminV extends PublicV { }

    static class Extra {
        public String note;
    }

    static class Bean {
        public final String name, password;

        @JsonUnwrapped
        public Extra extra;

        @JsonCreator
        public Bean(@JsonProperty("name") String n,
                @JsonView(AdminV.class) @JsonProperty("password") String p) {
            name = n; password = p;
        }
    }

    // Same, without `@JsonUnwrapped`: already gated before the fix
    static class PlainBean {
        public final String name, password;

        @JsonCreator
        public PlainBean(@JsonProperty("name") String n,
                @JsonView(AdminV.class) @JsonProperty("password") String p) {
            name = n; password = p;
        }
    }

    @JsonDeserialize(builder = User.Builder.class)
    static class User {
        public final String name, password;
        public final Extra extra;

        User(String n, String p, Extra e) {
            name = n; password = p; extra = e;
        }

        @JsonPOJOBuilder(withPrefix = "")
        static class Builder {
            String name, password;
            Extra extra;

            @JsonCreator
            public Builder(@JsonProperty("name") String n,
                    @JsonView(AdminV.class) @JsonProperty("password") String p) {
                name = n; password = p;
            }
            @JsonUnwrapped public Builder extra(Extra e) { extra = e; return this; }
            public User build() { return new User(name, password, extra); }
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private final static String JSON = a2q("{'password':'BYPASS','name':'alice','note':'n'}");

    @Test
    public void plainBeanCreatorPropViewHonored() throws Exception {
        PlainBean b = MAPPER.readerFor(PlainBean.class).withView(PublicV.class)
                .readValue(a2q("{'password':'BYPASS','name':'alice'}"));
        assertEquals("alice", b.name);
        assertNull(b.password);
    }

    @Test
    public void beanCreatorPropViewHonored() throws Exception {
        Bean b = MAPPER.readerFor(Bean.class).withView(PublicV.class).readValue(JSON);
        assertEquals("alice", b.name);
        assertNull(b.password, "password should be hidden by active view");
        assertEquals("n", b.extra.note);
    }

    @Test
    public void beanAdminViewStillWrites() throws Exception {
        Bean b = MAPPER.readerFor(Bean.class).withView(AdminV.class).readValue(JSON);
        assertEquals("BYPASS", b.password);
        assertEquals("n", b.extra.note);
    }

    @Test
    public void builderCreatorPropViewHonored() throws Exception {
        User u = MAPPER.readerFor(User.class).withView(PublicV.class).readValue(JSON);
        assertEquals("alice", u.name);
        assertNull(u.password, "password should be hidden by active view");
        assertEquals("n", u.extra.note);
    }

    @Test
    public void builderAdminViewStillWrites() throws Exception {
        User u = MAPPER.readerFor(User.class).withView(AdminV.class).readValue(JSON);
        assertEquals("BYPASS", u.password);
        assertEquals("n", u.extra.note);
    }
}
