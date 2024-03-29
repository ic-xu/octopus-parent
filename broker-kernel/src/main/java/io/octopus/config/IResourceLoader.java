package io.octopus.config;

import java.io.Reader;

public interface IResourceLoader {

    Reader loadDefaultResource();

    Reader loadResource(String relativePath);

    String getName();

   public class ResourceIsDirectoryException extends RuntimeException {

        private static final long serialVersionUID = -6969292229582764176L;

        public ResourceIsDirectoryException(String message) {
            super(message);
        }
    }

}
