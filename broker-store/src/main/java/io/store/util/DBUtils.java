package io.store.util;

import io.octopus.kernel.kernel.contants.BrokerConstants;
import io.store.persistence.DatabasesType;

/**
 * @author chenxu
 * @version 1
 */
public class DBUtils {

    public static DatabasesType getDB(String daName) {
        switch (daName) {
            case BrokerConstants.LEVEL_DB:
                return DatabasesType.LEVELDB;
            case BrokerConstants.H2:
                return DatabasesType.H2;
            case BrokerConstants.MEMORY:
                return DatabasesType.MEMORY;
            default:
                return null;
        }
    }
}
