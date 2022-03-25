package io.store.message;

public abstract  class EnqueuedMessage {

    private final int packageId;

    public EnqueuedMessage(int packageId){
        this.packageId = packageId;
    }

    /**
     * Releases any held resources. Must be called when the EnqueuedMessage is no
     * longer needed.
     */
    public void release() {}

    /**
     * Retains any held resources. Must be called when the EnqueuedMessage is added
     * to a store.
     */
    public void retain() {}

    /**
     * packageId of this message,
     */
    public int getPackageId() {
        return packageId;
    }
}