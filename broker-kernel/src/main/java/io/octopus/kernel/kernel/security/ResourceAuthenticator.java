package io.octopus.kernel.kernel.security;

import io.octopus.config.IResourceLoader;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Load user credentials from a text resource. Each line of the file is formatted as
 * "[username]:[sha256(password)]". The username mustn't contains : char.
 *
 * To encode your password from command line on Linux systems, you could use:
 *
 * <pre>
 *     echo -n "yourpassword" | sha256sum
 * </pre>
 *
 * NB -n is important because echo append a newline by default at the of string. -n avoid this
 * behaviour.
 */
public class ResourceAuthenticator implements IAuthenticator {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ResourceAuthenticator.class);

    private Map<String, String> identities = new HashMap<>();

    public ResourceAuthenticator(IResourceLoader resourceLoader, String resourceName) {
        try {
            MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsaex) {
            LOGGER.error("Can't find SHA-256 for password encoding", nsaex);
            throw new RuntimeException(nsaex);
        }

        LOGGER.info(String.format("Loading password %s %s", resourceLoader.getName(), resourceName));
        Reader reader = null;
        try {
            reader = resourceLoader.loadResource(resourceName);
            if (reader == null) {
                LOGGER.warn(String.format("Parsing not existing %s %s", resourceLoader.getName(), resourceName));
            } else {
                parse(reader);
            }
        } catch (IResourceLoader.ResourceIsDirectoryException e) {
            LOGGER.warn(String.format("Trying to parse directory %s", resourceName));
        } catch (ParseException pex) {
            LOGGER.warn(
                    String.format("Format error in parsing password %s %s", resourceLoader.getName(), resourceName),
                    pex);
        }
    }

    private void parse(Reader reader) throws ParseException {
        if (reader == null) {
            return;
        }

        BufferedReader br = new BufferedReader(reader);
        String line;
        try {
            while ((line = br.readLine()) != null) {
                int commentMarker = line.indexOf('#');
                if (commentMarker != -1) {
                    if (commentMarker == 0) {
                        // skip its a comment
                        continue;
                    } else {
                        // it's a malformed comment
                        throw new ParseException(line, commentMarker);
                    }
                } else {
                    if (line.isEmpty() || line.matches("^\\s*$")) {
                        // skip it's a black line
                        continue;
                    }

                    // split till the first space
                    int delimiterIdx = line.indexOf(':');
                    String username = line.substring(0, delimiterIdx).trim();
                    String password = line.substring(delimiterIdx + 1).trim();

                    identities.put(username, password);
                }
            }
        } catch (IOException ex) {
            throw new ParseException("Failed to read", 1);
        }
    }

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (username == null || password == null) {
            LOGGER.info("username or password was null");
            return false;
        }
        String foundPwq = identities.get(username);
        if (foundPwq == null) {
            return false;
        }
        String encodedPasswd = DigestUtils.sha256Hex(password);
        return foundPwq.equals(encodedPasswd);
    }

}
