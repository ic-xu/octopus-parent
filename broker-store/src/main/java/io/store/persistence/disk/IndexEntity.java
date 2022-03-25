package io.store.persistence.disk;

/**
 * 每个索引存储两个Long 数据，一个是当前数据，一个是下一个索引的位置，整个结构如下：
 * [当前数据索引,下一个索引位置]
 * [Long,int]
 *
 * @author chenxu
 * @version 1
 */
public class IndexEntity {

    private Long index;

    private Integer nextIndex;


    public Integer getNextIndex() {
        return nextIndex;
    }

    public Long getIndex() {
        return index;
    }
}
