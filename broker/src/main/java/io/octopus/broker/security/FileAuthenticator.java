package io.octopus.broker.security;

import io.octopus.base.config.FileResourceLoader;

/**
 * Load user credentials from a text file. Each line of the file is formatted as
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
 *
// * @deprecated user {@link ResourceAuthenticator} instead
 */
public class FileAuthenticator extends ResourceAuthenticator {

    public FileAuthenticator(String parent, String filePath) {
        super(new FileResourceLoader(parent), filePath);
    }
}
