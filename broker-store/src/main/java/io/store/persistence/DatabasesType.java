package io.store.persistence;

import io.octopus.contants.BrokerConstants;

/**
 * @author chenxu
 * @version 1
 */
public enum DatabasesType {
    MEMORY, H2, LEVELDB;

    public  static DatabasesType getDB(String daName) {
        switch (daName) {
            case BrokerConstants.LEVEL_DB:
                return LEVELDB;
            case BrokerConstants.H2:
                return H2;
            case BrokerConstants.MEMORY:
                return MEMORY;
            default:
                return null;
        }
    }
}
