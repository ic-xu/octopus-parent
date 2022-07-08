package io.octopus.kernel.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class ClassLoadUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassLoadUtils.class);

    /**
     *  load customer class
     * @param classLoader classLoader
     * @param className className (packageName + class simple Name)
     * @param intrface parent interface
     * @param constructorArgClass constructorArgClass
     * @param props props
     * @param <T> instance Class
     * @param <U> class of props
     * @return instance of <T>
     */
    public static <T, U> T loadClass(ClassLoader classLoader, String className, Class<T> intrface, Class<U> constructorArgClass, U props) {
        T instance;
        try {
            // check if constructor with constructor arg class parameter
            // exists
            LOGGER.info("Invoking constructor with {} argument. ClassName={}, interfaceName={}",
                    constructorArgClass.getName(), className, intrface.getName());
            instance = classLoader.loadClass(className)
                    .asSubclass(intrface)
                    .getConstructor(constructorArgClass)
                    .newInstance(props);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
            LOGGER.warn("Unable to invoke constructor with {} argument. ClassName={}, interfaceName={}, cause={}, " +
                            "errorMessage={}", constructorArgClass.getName(), className, intrface.getName(), ex.getCause(),
                    ex.getMessage());
            return null;
        } catch (NoSuchMethodException | InvocationTargetException e) {
            try {
                LOGGER.info("Invoking default constructor. ClassName={}, interfaceName={}", className, intrface.getName());
                // fallback to default constructor
                instance = classLoader.loadClass(className)
                        .asSubclass(intrface)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                    NoSuchMethodException | InvocationTargetException ex) {
                LOGGER.error("Unable to invoke default constructor. ClassName={}, interfaceName={}, cause={}, " +
                        "errorMessage={}", className, intrface.getName(), ex.getCause(), ex.getMessage());
                return null;
            }
        }

        return instance;
    }
}
