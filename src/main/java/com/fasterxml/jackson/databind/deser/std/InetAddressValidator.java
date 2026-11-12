package com.fasterxml.jackson.databind.deser.std;

import java.nio.ByteBuffer;

/**
 * Logic adapted from Google Guava's {@code InetAddresses.isInetAddress()} under the
 * Apache License 2.0:
 * https://github.com/google/guava/blob/master/guava/src/com/google/common/net/InetAddresses.java
 */
class InetAddressValidator
{
    private static final int IPV4_PART_COUNT = 4;
    private static final int IPV6_PART_COUNT = 8;

    private InetAddressValidator() { }

    static boolean isInetAddress(String ipString) {
        return _ipStringToBytes(ipString) != null;
    }

    private static byte[] _ipStringToBytes(String ipString) {
        boolean hasColon = false;
        boolean hasDot = false;
        int percentIndex = -1;
        for (int i = 0; i < ipString.length(); i++) {
            char c = ipString.charAt(i);
            if (c == '.') {
                hasDot = true;
            } else if (c == ':') {
                if (hasDot) {
                    return null;
                }
                hasColon = true;
            } else if (c == '%') {
                percentIndex = i;
                break;
            } else if (Character.digit(c, 16) == -1) {
                return null;
            }
        }

        if (hasColon) {
            if (hasDot) {
                ipString = _convertDottedQuadToHex(ipString);
                if (ipString == null) {
                    return null;
                }
            }
            if (percentIndex != -1) {
                ipString = ipString.substring(0, percentIndex);
            }
            return _textToNumericFormatV6(ipString);
        } else if (hasDot) {
            if (percentIndex != -1) {
                return null;
            }
            return _textToNumericFormatV4(ipString);
        }
        return null;
    }

    private static byte[] _textToNumericFormatV4(String ipString) {
        int dotCount = 0;
        for (int i = 0; i < ipString.length(); i++) {
            if (ipString.charAt(i) == '.') {
                dotCount++;
            }
        }
        if (dotCount + 1 != IPV4_PART_COUNT) {
            return null;
        }

        byte[] bytes = new byte[IPV4_PART_COUNT];
        int start = 0;
        for (int i = 0; i < IPV4_PART_COUNT; i++) {
            int end = ipString.indexOf('.', start);
            if (end == -1) {
                end = ipString.length();
            }
            try {
                bytes[i] = _parseOctet(ipString, start, end);
            } catch (NumberFormatException ex) {
                return null;
            }
            start = end + 1;
        }
        return bytes;
    }

    private static byte[] _textToNumericFormatV6(String ipString) {
        int delimiterCount = 0;
        for (int i = 0; i < ipString.length(); i++) {
            if (ipString.charAt(i) == ':') {
                delimiterCount++;
            }
        }
        if (delimiterCount < 2 || delimiterCount > IPV6_PART_COUNT) {
            return null;
        }
        int partsSkipped = IPV6_PART_COUNT - (delimiterCount + 1);
        boolean hasSkip = false;
        for (int i = 0; i < ipString.length() - 1; i++) {
            if (ipString.charAt(i) == ':' && ipString.charAt(i + 1) == ':') {
                if (hasSkip) {
                    return null;
                }
                hasSkip = true;
                partsSkipped++;
                if (i == 0) {
                    partsSkipped++;
                }
                if (i == ipString.length() - 2) {
                    partsSkipped++;
                }
            }
        }
        if (ipString.charAt(0) == ':' && ipString.charAt(1) != ':') {
            return null;
        }
        if (ipString.charAt(ipString.length() - 1) == ':'
                && ipString.charAt(ipString.length() - 2) != ':') {
            return null;
        }
        if (hasSkip && partsSkipped <= 0) {
            return null;
        }
        if (!hasSkip && delimiterCount + 1 != IPV6_PART_COUNT) {
            return null;
        }

        ByteBuffer rawBytes = ByteBuffer.allocate(2 * IPV6_PART_COUNT);
        try {
            int start = 0;
            if (ipString.charAt(0) == ':') {
                start = 1;
            }
            while (start < ipString.length()) {
                int end = ipString.indexOf(':', start);
                if (end == -1) {
                    end = ipString.length();
                }
                if (ipString.charAt(start) == ':') {
                    for (int i = 0; i < partsSkipped; i++) {
                        rawBytes.putShort((short) 0);
                    }
                } else {
                    rawBytes.putShort(_parseHextet(ipString, start, end));
                }
                start = end + 1;
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return rawBytes.array();
    }

    private static String _convertDottedQuadToHex(String ipString) {
        int lastColon = ipString.lastIndexOf(':');
        String initialPart = ipString.substring(0, lastColon + 1);
        String dottedQuad = ipString.substring(lastColon + 1);
        byte[] quad = _textToNumericFormatV4(dottedQuad);
        if (quad == null) {
            return null;
        }
        String penultimate = Integer.toHexString(((quad[0] & 0xff) << 8) | (quad[1] & 0xff));
        String ultimate = Integer.toHexString(((quad[2] & 0xff) << 8) | (quad[3] & 0xff));
        return initialPart + penultimate + ":" + ultimate;
    }

    private static byte _parseOctet(String ipString, int start, int end) {
        int length = end - start;
        if (length <= 0 || length > 3) {
            throw new NumberFormatException();
        }
        if (length > 1 && ipString.charAt(start) == '0') {
            throw new NumberFormatException();
        }
        int octet = 0;
        for (int i = start; i < end; i++) {
            octet *= 10;
            int digit = Character.digit(ipString.charAt(i), 10);
            if (digit < 0) {
                throw new NumberFormatException();
            }
            octet += digit;
        }
        if (octet > 255) {
            throw new NumberFormatException();
        }
        return (byte) octet;
    }

    private static short _parseHextet(String ipString, int start, int end) {
        int length = end - start;
        if (length <= 0 || length > 4) {
            throw new NumberFormatException();
        }
        int hextet = 0;
        for (int i = start; i < end; i++) {
            hextet = hextet << 4;
            hextet |= Character.digit(ipString.charAt(i), 16);
        }
        return (short) hextet;
    }
}
