package io.store.persistence.disk;

import io.octopus.kernel.checkpoint.CheckPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class CheckPointServerTest {

    CheckPointServer checkPointServer;

    @BeforeEach
    public void before() throws IOException {
        checkPointServer = new CheckPointServer();
    }

    @Test
    public void saveCheckPoint() {
        CheckPoint checkPoint = new CheckPoint();
        checkPoint.setPhysicalPoint(33);
        checkPoint.setReadPoint(21L);
        checkPoint.setWritePoint(21L);
        checkPoint.setWriteLimit(51);
        checkPointServer.saveCheckPoint(checkPoint,true);
    }

    @Test
    public void readCheckPoint() {
        for (int i = 0; i <100 ; i++) {
            saveCheckPoint();
        }
        CheckPoint checkPoint = checkPointServer.readCheckPoint();
        System.out.println(checkPoint);
    }
}