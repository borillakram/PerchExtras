package com.olziedev.parkour.model;

public class PlayerProgress {

    public static final int START = -1;

    private final String parkourName;
    private int checkpointIndex;
    private final boolean hadFlight;

    private long elapsedMillis;
    private long sessionStart;

    public PlayerProgress(String parkourName, int checkpointIndex, boolean hadFlight, long elapsedMillis) {
        this.parkourName = parkourName;
        this.checkpointIndex = checkpointIndex;
        this.hadFlight = hadFlight;
        this.elapsedMillis = elapsedMillis;
        this.sessionStart = 0L;
    }

    public String getParkourName() {
        return parkourName;
    }

    public boolean hadFlight() {
        return hadFlight;
    }

    public void startSession(long now) {
        this.sessionStart = now;
    }

    public void pauseSession(long now) {
        if (sessionStart > 0) {
            elapsedMillis += now - sessionStart;
            sessionStart = 0L;
        }
    }

    public boolean isRunning() {
        return sessionStart > 0;
    }

    public long getElapsed(long now) {
        return elapsedMillis + (sessionStart > 0 ? now - sessionStart : 0L);
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public int getCheckpointIndex() {
        return checkpointIndex;
    }

    public void setCheckpointIndex(int checkpointIndex) {
        this.checkpointIndex = checkpointIndex;
    }

    public int getStage() {
        return checkpointIndex + 1;
    }
}
