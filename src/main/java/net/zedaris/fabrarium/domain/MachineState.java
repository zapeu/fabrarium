package net.zedaris.fabrarium.domain;

public record MachineState(
        MachineKey key,
        MachinePhase phase,
        int inputCount,
        int outputCount,
        long readyAtEpochMilli,
        long revision) {

    public MachineState {
        if (key == null || phase == null) {
            throw new IllegalArgumentException("key and phase are required");
        }
        if (inputCount < 0 || outputCount < 0 || readyAtEpochMilli < 0 || revision < 0) {
            throw new IllegalArgumentException("machine counters cannot be negative");
        }
    }

    public static MachineState idle(MachineKey key) {
        return new MachineState(key, MachinePhase.IDLE, 0, 0, 0, 0);
    }

    public MachineState start(long readyAt) {
        if (phase != MachinePhase.IDLE) {
            throw new IllegalStateException("machine is not idle");
        }
        return new MachineState(key, MachinePhase.PROCESSING, 2, 0, readyAt, revision + 1);
    }

    public MachineState finish(long now) {
        if (phase != MachinePhase.PROCESSING || now < readyAtEpochMilli) {
            return this;
        }
        return new MachineState(key, MachinePhase.READY, 0, 2, 0, revision + 1);
    }

    public MachineState collect() {
        if (phase != MachinePhase.READY) {
            throw new IllegalStateException("machine output is not ready");
        }
        return new MachineState(key, MachinePhase.IDLE, 0, 0, 0, revision + 1);
    }
}
