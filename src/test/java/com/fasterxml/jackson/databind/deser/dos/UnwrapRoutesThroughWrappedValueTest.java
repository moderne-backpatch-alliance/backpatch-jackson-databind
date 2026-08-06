package com.fasterxml.jackson.databind.deser.dos;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Covers the ONE line of this backpatch that is not upstream text for the CVE being fixed:
 * StdDeserializer._deserializeFromArray's unwrap branch calls _deserializeWrappedValue(),
 * where 2.10.5.1 called deserialize() directly.
 *<p>
 * It matters for two reasons. The nested-array guard added for CVE-2022-42003 lives in
 * _deserializeWrappedValue, so without this routing the guard is unreachable from
 * _deserializeFromArray and the fix is cosmetic on that path. And 2.10's own javadoc on
 * _deserializeFromArray already promises the delegation ("will handle actual decoding by
 * calling {@link #_deserializeWrappedValue}") — the code simply never did it. 2.12 makes
 * the same call.
 *<p>
 * No deserializer in this tree overrides _deserializeWrappedValue, which is exactly why
 * nothing else can detect the change: this test supplies the subclass that can.
 */
public class UnwrapRoutesThroughWrappedValueTest extends BaseMapTest
{
    static class Value {
        public final String text;
        Value(String text) { this.text = text; }
    }

    /**
     * Routes START_ARRAY into _deserializeFromArray (as the std scalar deserializers do)
     * and counts how often the documented _deserializeWrappedValue hook is reached.
     */
    static class ValueDeserializer extends StdDeserializer<Value>
    {
        private static final long serialVersionUID = 1L;

        int wrappedValueCalls = 0;

        ValueDeserializer() { super(Value.class); }

        @Override
        public Value deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.START_ARRAY)) {
                return _deserializeFromArray(p, ctxt);
            }
            return new Value(p.getValueAsString());
        }

        @Override
        protected Value _deserializeWrappedValue(JsonParser p, DeserializationContext ctxt)
            throws IOException
        {
            ++wrappedValueCalls;
            return super._deserializeWrappedValue(p, ctxt);
        }
    }

    private ObjectMapper mapperWith(ValueDeserializer deser) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Value.class, deser);
        return jsonMapperBuilder()
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .addModule(module)
                .build();
    }

    // Pre-fix _deserializeFromArray calls deserialize() directly, so the hook is never
    // reached and this count stays 0.
    public void testUnwrapReachesWrappedValueHook() throws Exception
    {
        ValueDeserializer deser = new ValueDeserializer();
        Value v = mapperWith(deser).readValue("[\"abc\"]", Value.class);

        assertNotNull(v);
        assertEquals("abc", v.text);
        assertEquals("_deserializeFromArray must unwrap via _deserializeWrappedValue,"
                + " which is where the nested-array guard lives",
                1, deser.wrappedValueCalls);
    }

    // The consequence that matters: because the unwrap goes through
    // _deserializeWrappedValue, the CVE-2022-42003 guard now bites on this path too.
    // Pre-fix this document deserializes happily instead of being refused.
    public void testNestedArrayRefusedOnThisPath() throws Exception
    {
        ValueDeserializer deser = new ValueDeserializer();
        try {
            mapperWith(deser).readValue("[[\"abc\"]]", Value.class);
            fail("Nested wrapper arrays must be refused on the _deserializeFromArray path");
        } catch (MismatchedInputException e) {
            verifyException(e, "nested Arrays not allowed");
        }
    }
}
