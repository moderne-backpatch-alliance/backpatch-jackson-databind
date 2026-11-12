package com.fasterxml.jackson.databind.ext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.ServiceLoader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import static java.lang.Character.isLetter;

/**
 * @since 2.8
 */
public class NioPathDeserializer extends StdScalarDeserializer<Path>
{
    private static final long serialVersionUID = 1;

    public final static Collection<String> DEFAULT_ALLOWED_SCHEMES = Collections.singletonList("file");

    private static final boolean areWindowsFilePathsSupported;
    static {
        boolean isWindowsRootFound = false;
        for (File file : File.listRoots()) {
            String path = file.getPath();
            if (path.length() >= 2 && isLetter(path.charAt(0)) && path.charAt(1) == ':') {
                isWindowsRootFound = true;
                break;
            }
        }
        areWindowsFilePathsSupported = isWindowsRootFound;
    }

    protected final Collection<String> _allowedSchemes;

    public NioPathDeserializer() { this(DEFAULT_ALLOWED_SCHEMES); }

    public NioPathDeserializer(Collection<String> allowedSchemes) {
        super(Path.class);
        if (allowedSchemes == null) {
            throw new IllegalArgumentException("Argument `allowedSchemes` must not be null");
        }
        _allowedSchemes = allowedSchemes;
    }

    @Override
    public Path deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (Path) ctxt.handleUnexpectedToken(Path.class, p);
        }

        final String value = p.getText();

        // If someone gives us an input with no : at all, treat as local path, instead of failing
        // with invalid URI.
        if (value.indexOf(':') < 0) {
            return Paths.get(value);
        }

        if (areWindowsFilePathsSupported) {
            if (value.length() >= 2 && isLetter(value.charAt(0)) && value.charAt(1) == ':') {
                return Paths.get(value);
            }
        }

        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return (Path) ctxt.handleInstantiationProblem(handledType(), value, e);
        }
        final String scheme = uri.getScheme();
        if (scheme != null && !_isSchemeAllowed(scheme)) {
            return (Path) ctxt.handleWeirdStringValue(Path.class, value,
                    "scheme '%s' not allowed for Path deserialization (allowed: %s)",
                    scheme, _allowedSchemesDesc());
        }
        try {
            return Paths.get(uri);
        } catch (FileSystemNotFoundException cause) {
            try {
                // We want to use the current thread's context class loader, not system class loader that is used in Paths.get():
                for (FileSystemProvider provider : ServiceLoader.load(FileSystemProvider.class)) {
                    if (provider.getScheme().equalsIgnoreCase(scheme)) {
                        return provider.getPath(uri);
                    }
                }
                return (Path) ctxt.handleInstantiationProblem(handledType(), value, cause);
            } catch (Throwable e) {
                e.addSuppressed(cause);
                return (Path) ctxt.handleInstantiationProblem(handledType(), value, e);
            }
        } catch (Throwable e) {
            return (Path) ctxt.handleInstantiationProblem(handledType(), value, e);
        }
    }

    protected boolean _isSchemeAllowed(String scheme) {
        if (_allowedSchemes.contains(scheme)) {
            return true;
        }
        for (String allowed : _allowedSchemes) {
            if (scheme.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    protected String _allowedSchemesDesc() {
        StringBuilder sb = new StringBuilder("[");
        for (String scheme : _allowedSchemes) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append('"').append(scheme).append('"');
        }
        return sb.append(']').toString();
    }
}
