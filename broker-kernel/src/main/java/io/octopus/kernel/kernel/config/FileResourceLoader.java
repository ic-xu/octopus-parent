package io.octopus.kernel.kernel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileResourceLoader implements IResourceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileResourceLoader.class);

    private final File defaultFile;
    private final String parentPath;

    public FileResourceLoader() {
        this((File) null);
    }

    public FileResourceLoader(File defaultFile) {
        this(defaultFile, System.getProperty("octopus.path", null));
    }

    public FileResourceLoader(String parentPath) {
        this(null, parentPath);
    }

    public FileResourceLoader(File defaultFile, String parentPath) {
        this.defaultFile = defaultFile;
        this.parentPath = parentPath;
    }

    @Override
    public Reader loadDefaultResource() {
        if (defaultFile != null) {
            return loadResource(defaultFile);
        } else {
            throw new IllegalArgumentException("Default file not set!");
        }
    }

    @Override
    public Reader loadResource(String relativePath) {
//        if(parentPath==null)
//            return loadResource(new File(defaultFile.getParent(), relativePath));
        return loadResource(new File(parentPath, relativePath));
    }

    public Reader loadResource(File f) {
        LOGGER.info("Loading file. Path = {}.", f.getAbsolutePath());
        if (f.isDirectory()) {
            LOGGER.error("The given file is a directory. Path = {}.", f.getAbsolutePath());
            throw new ResourceIsDirectoryException("File \"" + f + "\" is a directory!");
        }
        try {
            return Files.newBufferedReader(f.toPath(), UTF_8);
        } catch (IOException e) {
            LOGGER.error("The file does not exist. Path = {}.", f.getAbsolutePath());
            return null;
        }
    }

    @Override
    public String getName() {
        return "file";
    }

}
