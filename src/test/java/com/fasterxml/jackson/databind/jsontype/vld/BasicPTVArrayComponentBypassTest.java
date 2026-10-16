package com.fasterxml.jackson.databind.jsontype.vld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * [databind#5981] {@code BasicPolymorphicTypeValidator.allowIfSubTypeIsArray()} must not
 * approve arrays whose component type would itself be denied by the configured
 * sub-class allow-list.
 */
public class BasicPTVArrayComponentBypassTest extends BaseMapTest
{
    // Fully-qualified name of this test class -- works as a name-prefix matcher
    // for every nested helper (SafePayload, FakeGadget, ObjectWrapper) via their
    // "outer.Name$Nested" form. Used in namePrefixAllowsBothElementAndArray.
    private static final String OWN_CLASS_NAME_PREFIX =
            BasicPTVArrayComponentBypassTest.class.getName();

    /**
     * Records every constructor invocation; lets the tests prove that an
     *  un-allow-listed type is not actually instantiated.
     */
    static final List<String> INSTANTIATIONS = new ArrayList<String>();

    /**
     * Stand-in "unsafe" type. Completely benign in itself -- only side effect is
     * recording its own instantiation. {@code final} so default typing emits no
     * per-element type id, which is the configuration the bypass exploited.
     */
    static final class FakeGadget {
        public String cmd;
        public FakeGadget() {
            INSTANTIATIONS.add(FakeGadget.class.getName());
        }
    }

    /**
     * Type that IS in the allow-list, used to verify the positive path through
     * the unwrap-then-match fall-through.
     */
    static final class SafePayload {
        public int data;
        public SafePayload() { }
    }

    static final class ObjectWrapper {
        public Object value;
        protected ObjectWrapper() { }
    }

    // For [databind#5981]: with the validator engaged (no allowIfBaseType escape
    // hatch into LaissezFaire), a direct FakeGadget value must be denied AND the
    // same FakeGadget wrapped as FakeGadget[] must also be denied -- the element
    // type must not bypass the allow-list just because it is wrapped in an array.
    public void testDirectGadgetAndGadgetArrayBothDenied() throws Exception
    {
        ObjectMapper mapper = mapperWithSafePayloadAndArrays();

        // (a) Direct FakeGadget: denied. Sanity check that the validator is actually
        // engaged with this configuration (i.e. not swapped out for LaissezFaire).
        final String classId = FakeGadget.class.getName();
        final String directJson = "{\"value\":[\"" + classId + "\",{\"cmd\":\"x\"}]}";
        INSTANTIATIONS.clear();
        try {
            mapper.readValue(directJson, ObjectWrapper.class);
            fail("Direct FakeGadget must be denied (no array, no SafePayload match)");
        } catch (InvalidTypeIdException e) {
            verifyException(e, classId);
        }
        assertEquals("FakeGadget must not be instantiated when its bare class is denied;"
                + " observed=" + INSTANTIATIONS, 0, INSTANTIATIONS.size());

        // (b) FakeGadget wrapped as FakeGadget[]: pre-fix this slipped through,
        // because the array matcher approved every array regardless of element type.
        // Post-fix the validator unwraps the array and runs the innermost element
        // type through the same allow-list, which denies FakeGadget.
        final String arrayId = "[L" + classId + ";";
        final String arrayJson = "{\"value\":[\"" + arrayId + "\",[{\"cmd\":\"x\"}]]}";
        INSTANTIATIONS.clear();
        try {
            mapper.readValue(arrayJson, ObjectWrapper.class);
            fail("FakeGadget[] must be denied because FakeGadget is not allow-listed");
        } catch (InvalidTypeIdException e) {
            verifyException(e, arrayId);
        }
        assertEquals("FakeGadget must not be instantiated when its array form is denied;"
                + " observed=" + INSTANTIATIONS, 0, INSTANTIATIONS.size());
    }

    // For [databind#5981]: the unwrap loop in validateSubType() must recurse for
    // nested arrays so that FakeGadget[][] is denied for the same reason
    // FakeGadget[] is denied -- the innermost element is what matters.
    public void testNestedGadgetArrayAlsoDenied() throws Exception
    {
        ObjectMapper mapper = mapperWithSafePayloadAndArrays();

        final String classId = FakeGadget.class.getName();
        final String nestedArrayId = "[[L" + classId + ";";
        // FakeGadget[][] with a single inner FakeGadget[] containing one FakeGadget.
        // If the unwrap stopped at one level (e.g. FakeGadget[]), the outer match
        // would short-circuit ALLOWED; the recursive unwrap to FakeGadget is what
        // makes this denial correct.
        final String json = "{\"value\":[\"" + nestedArrayId + "\","
                + "[[{\"cmd\":\"x\"}]]]}";
        INSTANTIATIONS.clear();
        try {
            mapper.readValue(json, ObjectWrapper.class);
            fail("FakeGadget[][] must be denied: innermost element FakeGadget is not allow-listed");
        } catch (InvalidTypeIdException e) {
            verifyException(e, nestedArrayId);
        }
        assertEquals("FakeGadget must not be instantiated when its nested-array form is denied;"
                + " observed=" + INSTANTIATIONS, 0, INSTANTIATIONS.size());
    }

    // For [databind#5981]: positive path. Confirms the unwrap-then-match fall-through
    // works for an allow-listed concrete component type -- SafePayload[] is approved
    // because validateSubType unwraps to SafePayload and the existing allow-list
    // matcher matches it.
    public void testAllowListedConcreteComponentArrayAccepted() throws Exception
    {
        ObjectMapper mapper = mapperWithSafePayloadAndArrays();

        final String arrayId = "[L" + SafePayload.class.getName() + ";";
        final String json = "{\"value\":[\"" + arrayId + "\",[{\"data\":42}]]}";

        ObjectWrapper out = mapper.readValue(json, ObjectWrapper.class);
        assertNotNull(out);
        assertNotNull(out.value);
        assertEquals(SafePayload[].class, out.value.getClass());
        SafePayload[] arr = (SafePayload[]) out.value;
        assertEquals(1, arr.length);
        assertEquals(42, arr[0].data);
    }

    // For [databind#5981]: primitive-element arrays are accepted via the primitive
    // exemption (no allow-list entry needed for {@code int}). Primitive arrays
    // cannot carry gadget chains, so this exemption is safe.
    public void testPrimitiveComponentArrayAccepted() throws Exception
    {
        ObjectMapper mapper = mapperWithSafePayloadAndArrays();

        // JVM internal name for int[] is "[I"
        final String json = "{\"value\":[\"[I\",[1,2,3]]}";
        ObjectWrapper out = mapper.readValue(json, ObjectWrapper.class);
        assertNotNull(out);
        assertEquals(int[].class, out.value.getClass());
        assertTrue(Arrays.equals(new int[] { 1, 2, 3 }, (int[]) out.value));
    }

    // For [databind#5988]: name-prefix matchers must also apply to array element
    // types after unwrap. Previously validateSubType() consulted only the class-
    // based matchers, so allowIfSubType("com.fasterxml.jackson...") (a name matcher)
    // would allow SafePayload directly but reject SafePayload[].
    public void testNamePrefixAllowsBothElementAndArray() throws Exception
    {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Name prefix matcher only -- no class matcher is registered.
                .allowIfSubType(OWN_CLASS_NAME_PREFIX)
                .allowIfSubTypeIsArray()
                .build();
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();

        final String arrayId = "[L" + SafePayload.class.getName() + ";";
        final String json = "{\"value\":[\"" + arrayId + "\",[{\"data\":42}]]}";

        ObjectWrapper out = mapper.readValue(json, ObjectWrapper.class);
        assertNotNull(out);
        assertEquals(SafePayload[].class, out.value.getClass());
        SafePayload[] arr = (SafePayload[]) out.value;
        assertEquals(1, arr.length);
        assertEquals(42, arr[0].data);
    }

    // For [databind#5988]: even after the name-matcher fix, an array whose element
    // type is NOT covered by the name prefix must still be denied.
    public void testNamePrefixDeniesUnmatchedArrayElement() throws Exception
    {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Name prefix that intentionally does NOT cover FakeGadget.
                .allowIfSubType("nonexistent.package.")
                .allowIfSubTypeIsArray()
                .build();
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();

        final String classId = FakeGadget.class.getName();
        final String arrayId = "[L" + classId + ";";
        final String json = "{\"value\":[\"" + arrayId + "\",[{\"cmd\":\"x\"}]]}";

        INSTANTIATIONS.clear();
        try {
            mapper.readValue(json, ObjectWrapper.class);
            fail("FakeGadget[] must be denied: FakeGadget is not covered by name prefix");
        } catch (InvalidTypeIdException e) {
            verifyException(e, arrayId);
        }
        assertEquals(0, INSTANTIATIONS.size());
    }

    private ObjectMapper mapperWithSafePayloadAndArrays() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(SafePayload.class)
                .allowIfSubTypeIsArray()
                .build();
        return jsonMapperBuilder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();
    }
}
