package no.nav.vedtak.felles.prosesstask.api;

import java.util.Objects;

public record ProsessTaskVeto(boolean isVeto, Long prosessTaskId, Long blokkertAvProsessTaskId, String begrunnelse) {

    public ProsessTaskVeto {
        Objects.requireNonNull(prosessTaskId, "prosessTaskId");
    }

    public ProsessTaskVeto(boolean veto, Long prosessTaskId) {
        this(veto, prosessTaskId, null, null);
    }

    public ProsessTaskVeto(boolean veto, Long prosessTaskId, Long blokkertAvProsessTaskId) {
        this(veto, prosessTaskId, blokkertAvProsessTaskId, null);
    }

    public Long getBlokkertAvProsessTaskId() {
        return blokkertAvProsessTaskId;
    }

    public Long getProsessTaskId() {
        return prosessTaskId;
    }

    public String getBegrunnelse() {
        return begrunnelse;
    }
}
