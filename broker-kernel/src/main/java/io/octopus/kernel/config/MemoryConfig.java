package io.octopus.kernel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

/**
 * Configuration backed by memory.
 * @author user
 */
public class MemoryConfig extends IConfig {

   private final static Logger LOGGER = LoggerFactory.getLogger(MemoryConfig.class);

    private final Properties propertiesStore = new Properties();

    public MemoryConfig(Properties properties) {
        assignDefaults();
        for (Map.Entry<Object, Object> entrySet : properties.entrySet()) {
            propertiesStore.put(entrySet.getKey(), entrySet.getValue());
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
        }catch (Exception e){
            return defaultValue;
        }
    }

    @Override
    public Long getLongProperty(String name, Long defaultValue) {
        try {
            return Long.parseLong(getProperty(name));
        }catch (Exception e){
            return defaultValue;
        }
    }

    @Override
    public IResourceLoader getResourceLoader() {
        return new FileResourceLoader();
    }

    @Override
    public void printEnvironment() {
        LOGGER.info("begin printf environment");
        LOGGER.info("*****************************************************************************");
        propertiesStore.forEach((key,value)-> LOGGER.info("[ {}  =  {} ]",key,value));
        LOGGER.info("*****************************************************************************");
        LOGGER.info("end printf environment");
    }
}
