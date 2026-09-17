package com.fasterxml.jackson.databind.deser.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.BaseMapTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * [databind#5974] / CVE-2026-59888: {@code POJOPropertiesCollector._removeUnwantedProperties()}
 * records an ignored property's implicit name in {@code _ignoredPropertyNames} BEFORE
 * {@code _renameUsing()} applies the configured naming strategy, so the renamed JSON key was
 * not recognised as ignored.
 *
 * The advisory's own scenario is a Record component, which cannot be expressed on this
 * baseline's JDK 8 build -- upstream's test for it lives under {@code src/test-jdk17/} and is
 * shipped here too, inert unless someone builds under JDK 17. This test drives THE SAME two
 * added lines through a path that exists on JDK 8: an {@code @JsonIgnore} field with an
 * explicitly annotated getter survives {@code removeIgnored()}, cannot deserialize, and so has
 * its ignoral collected under the pre-rename name -- exactly the state the fix repairs.
 *
 * Without the fix the renamed key is simply unknown and deserialization fails; with it the key
 * is recognised as ignored. The second method is the negative control: the same document
 * against the same shape WITHOUT a naming strategy, which was never affected.
 */
public class IgnoredPropertyRenamePropagation5974Test extends BaseMapTest
{

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class RenamedIgnored {
        public String username;
        @JsonIgnore
        private String internalRole;
        @JsonProperty
        public String getInternalRole() { return internalRole; }
    }

    static class PlainIgnored {
        public String username;
        @JsonIgnore
        private String internalRole;
        @JsonProperty
        public String getInternalRole() { return internalRole; }
    }

    private static final String DOC_RENAMED =
            "{\"username\":\"alice\",\"internal_role\":\"ADMIN\"}";
    private static final String DOC_PLAIN =
            "{\"username\":\"alice\",\"internalRole\":\"ADMIN\"}";

    private final ObjectMapper MAPPER = newJsonMapper();

    public void testRenamedIgnoredPropertyIsStillIgnored() throws Exception
    {
        RenamedIgnored result = MAPPER.readValue(DOC_RENAMED, RenamedIgnored.class);
        assertEquals("alice", result.username);
        assertNull("@JsonIgnore must survive the naming-strategy rename", result.internalRole);
    }

    public void testUnrenamedIgnoredPropertyIsStillIgnored() throws Exception
    {
        PlainIgnored result = MAPPER.readValue(DOC_PLAIN, PlainIgnored.class);
        assertEquals("alice", result.username);
        assertNull(result.internalRole);
    }
}
