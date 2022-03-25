package io.octopus.base.checkpoint;

public class CheckPoint {

    private Long saveTime;

    private Long readPoint;

    private Long writePoint;

    private int physicalPoint;

    private int writeLimit;


    public int getPhysicalPoint() {
        return physicalPoint;
    }

    public void setPhysicalPoint(int physicalPoint) {
        this.physicalPoint = physicalPoint;
    }

    public Long getSaveTime() {
        return saveTime;
    }

    public void setSaveTime(Long saveTime) {
        this.saveTime = saveTime;
    }

    public Long getReadPoint() {
        return readPoint;
    }

    public void setReadPoint(Long readPoint) {
        this.readPoint = readPoint;
    }

    public Long getWritePoint() {
        return writePoint;
    }

    public void setWritePoint(Long writePoint) {
        this.writePoint = writePoint;
    }

    public int getWriteLimit() {
        return writeLimit;
    }

    public void setWriteLimit(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    @Override
    public String toString() {
        return "CheckPoint{" +
                "saveTime=" + saveTime +
                ", readPoint=" + readPoint +
                ", writePoint=" + writePoint +
                ", physicalPoint=" + physicalPoint +
                ", writeLimit=" + writeLimit +
                '}';
    }
}
