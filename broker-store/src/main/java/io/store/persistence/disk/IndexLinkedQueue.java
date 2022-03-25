package io.store.persistence.disk;

import java.util.AbstractQueue;
import java.util.Iterator;

/**
 * @author chenxu
 * @version 1
 * @date $ $
 */
public class IndexLinkedQueue extends AbstractQueue<IndexEntity> {

    private IndexEntity headIndexEntity;

    private IndexEntity tailIndexEntity;

    private final  IndexFile indexFile;

    public IndexLinkedQueue(IndexFile indexFile) {
        this.indexFile = indexFile;
    }



    @Override
    public Iterator<IndexEntity> iterator() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean offer(IndexEntity indexEntity) {
        return false;
    }

    @Override
    public IndexEntity poll() {
        return null;
    }

    @Override
    public IndexEntity peek() {
        return null;
    }
}
