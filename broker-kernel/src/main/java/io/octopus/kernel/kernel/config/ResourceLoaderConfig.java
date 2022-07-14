package io.octopus.kernel.kernel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.text.ParseException;
import java.util.Properties;

/**
 * Configuration that loads config stream from a {@link IResourceLoader} instance.
 */
public class ResourceLoaderConfig extends IConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLoaderConfig.class);

    private final Properties propertiesStore;
    private final IResourceLoader resourceLoader;

    public ResourceLoaderConfig(IResourceLoader resourceLoader) {
        this(resourceLoader, null);
    }

    public ResourceLoaderConfig(IResourceLoader resourceLoader, String configName) {
        LOGGER.info("Loading configuration. ResourceLoader = {}, configName = {}.", resourceLoader.getName(), configName);
        this.resourceLoader = resourceLoader;

        /*
         * If we use a conditional operator, the loadResource() and the loadDefaultResource()
         * methods will be always called. This makes the log traces confusing.
         */
///https://apis.mindlinker.com/rsc/api/v1/client/third-part/endpoints?size=999999&page=1&deviceSerialNumber=52191028001559


        Reader configReader;
        if (configName != null) {
            configReader = resourceLoader.loadResource(configName);
        } else {
            configReader = resourceLoader.loadDefaultResource();
        }

        if (configReader == null) {
            LOGGER.error(
                    "The resource loader returned no configuration reader. ResourceLoader = {}, configName = {}.",
                    resourceLoader.getName(),
                    configName);
            throw new IllegalArgumentException("Can't locate " + resourceLoader.getName() + " \"" + configName + "\"");
        }

        LOGGER.info(
                "Parsing configuration properties. ResourceLoader = {}, configName = {}.",
                resourceLoader.getName(),
                configName);
        ConfigurationParser confParser = new ConfigurationParser();
        propertiesStore = confParser.getProperties();
        assignDefaults();
        try {
            confParser.parse(configReader);
        } catch (ParseException pex) {
            LOGGER.warn(
                    "Unable to parse configuration properties. Using default configuration. "
                            + "ResourceLoader = {}, configName = {}, cause = {}, errorMessage = {}.",
                    resourceLoader.getName(),
                    configName,
                    pex.getCause(),
                    pex.getMessage());
        }
    }

    @Override
    public void setProperty(String name, String value) {
        propertiesStore.setProperty(name, value);
    }

    @Override
    public String getProperty(String name) {
        return propertiesStore.getProperty(name);
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        return propertiesStore.getProperty(name, defaultValue);
    }

    @Override
    public Integer getIntegerProperty(String name, Integer defaultValue) {
        try {
            return Integer.parseInt(getProperty(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public Long getLongProperty(String name, Long defaultValue) {
        try {
            return Long.parseLong(getProperty(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public IResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    @Override
    public void printEnvironment() {
        LOGGER.info(" ");
        LOGGER.info("begin printf environment");
        LOGGER.info("*****************************************************************************");
        propertiesStore.forEach((key,value)-> LOGGER.info("** [ {}  =  {} ]",key,value));
        LOGGER.info("*****************************************************************************");
        LOGGER.info("end printf environment");
    }
}
