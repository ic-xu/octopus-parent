package io.octopus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ClasspathResourceLoader implements IResourceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathResourceLoader.class);

    private final String defaultResource;
    private final ClassLoader classLoader;

    public ClasspathResourceLoader() {
        this(IConfig.DEFAULT_CONFIG);
    }

    public ClasspathResourceLoader(String defaultResource) {
        this(defaultResource, Thread.currentThread().getContextClassLoader());
    }

    public ClasspathResourceLoader(String defaultResource, ClassLoader classLoader) {
        this.defaultResource = defaultResource;
        this.classLoader = classLoader;
    }

    @Override
    public Reader loadDefaultResource() {
        return loadResource(defaultResource);
    }

    @Override
    public Reader loadResource(String relativePath) {
        LOGGER.info("Loading resource. RelativePath = {}.", relativePath);
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(relativePath);
        return is != null ? new InputStreamReader(is, StandardCharsets.UTF_8) : null;
    }

    @Override
    public String getName() {
        return "classpath resource";
    }

}
