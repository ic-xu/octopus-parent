package io.store.message;

public final class PubRelMarker extends EnqueuedMessage {

    public PubRelMarker(int packageId) {
        super(packageId);
    }
}