package com.fasterxml.jackson.databind.node;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInput;

import com.fasterxml.jackson.databind.BaseMapTest;

/**
 * [CVE-2021-46877] {@code NodeSerialization.readExternal()} must not size its buffer
 * from the attacker-supplied length prefix in one eager allocation.
 *<p>
 * Upstream's own test for #3328 (NodeJDKSerializationTest.testBigArrayNodeSerialization)
 * round-trips an HONEST stream, so it passes both before and after the fix — it proves the
 * new chunked read still reads correctly, not that the DoS is closed. This test supplies a
 * hostile length prefix instead, which is the actual vulnerability.
 *<p>
 * The assertion is on the size of the largest single read the implementation ASKS for,
 * not on OutOfMemoryError: whether a 200 MB eager allocation actually OOMs depends on the
 * heap the suite happens to run with, and a security test must not be decided by that.
 * "Bytes requested in one go" is exactly what the CVE is about and is deterministic.
 */
public class NodeSerializationForgedLengthTest extends BaseMapTest
{
    // Well above LONGEST_EAGER_ALLOC (100_000) so the pre-fix path is unmistakably eager,
    // but small enough that the pre-fix allocation SUCCEEDS on a normal heap — so the
    // pre-fix failure is a clean assertion failure rather than an OutOfMemoryError whose
    // occurrence depends on -Xmx.
    private final static int FORGED_LENGTH = 200_000_000;

    // Deliberately a literal rather than NodeSerialization.LONGEST_EAGER_ALLOC: that
    // constant does not exist before the fix, so referencing it would make this test fail
    // to COMPILE against the vulnerable version instead of failing an assertion against it.
    // A regression test for a CVE has to be runnable on the version that has the CVE.
    // Keep in sync with NodeSerialization.LONGEST_EAGER_ALLOC.
    private final static int MAX_EAGER_ALLOC = 100_000;

    /**
     * Minimal hostile {@link ObjectInput}: claims {@link #FORGED_LENGTH} bytes are coming,
     * records the largest single read requested, then reports end-of-stream. Everything
     * else is unreachable for this code path and refuses loudly rather than returning
     * plausible data.
     */
    static class ForgedLengthInput implements ObjectInput
    {
        int largestReadRequested = 0;
        private boolean lengthRead = false;

        @Override
        public int readInt() {
            if (lengthRead) {
                throw new IllegalStateException("readExternal should read the length exactly once");
            }
            lengthRead = true;
            return FORGED_LENGTH;
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            largestReadRequested = Math.max(largestReadRequested, len);
            // No data actually follows the forged prefix. Post-fix this ends the read
            // after one bounded segment; pre-fix it is only reached once the full
            // attacker-sized array has already been allocated.
            throw new EOFException("no data behind the forged length prefix");
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            readFully(b, 0, b.length);
        }

        // ---- everything below is not part of this code path ----

        private RuntimeException unexpected() {
            return new UnsupportedOperationException("not used by NodeSerialization.readExternal");
        }

        @Override public Object readObject() { throw unexpected(); }
        @Override public int read() { throw unexpected(); }
        @Override public int read(byte[] b) { throw unexpected(); }
        @Override public int read(byte[] b, int off, int len) { throw unexpected(); }
        @Override public long skip(long n) { throw unexpected(); }
        @Override public int available() { throw unexpected(); }
        @Override public void close() { }
        @Override public int skipBytes(int n) { throw unexpected(); }
        @Override public boolean readBoolean() { throw unexpected(); }
        @Override public byte readByte() { throw unexpected(); }
        @Override public int readUnsignedByte() { throw unexpected(); }
        @Override public short readShort() { throw unexpected(); }
        @Override public int readUnsignedShort() { throw unexpected(); }
        @Override public char readChar() { throw unexpected(); }
        @Override public long readLong() { throw unexpected(); }
        @Override public float readFloat() { throw unexpected(); }
        @Override public double readDouble() { throw unexpected(); }
        @Override public String readLine() { throw unexpected(); }
        @Override public String readUTF() { throw unexpected(); }
    }

    public void testForgedLengthPrefixDoesNotAllocateEagerly() throws Exception
    {
        NodeSerialization ser = new NodeSerialization();
        ForgedLengthInput in = new ForgedLengthInput();

        try {
            ser.readExternal(in);
            fail("Should not complete: there is no data behind the forged length prefix");
        } catch (EOFException e) {
            // expected: the read runs out of data, which is the honest outcome for a
            // stream whose prefix lied. What matters is HOW MUCH was allocated first.
        }

        assertTrue("readExternal asked for " + in.largestReadRequested
                        + " bytes in a single read from a stream claiming " + FORGED_LENGTH
                        + "; a forged length prefix must not size the allocation"
                        + " (expected at most " + MAX_EAGER_ALLOC + ")",
                in.largestReadRequested <= MAX_EAGER_ALLOC);
    }
}
