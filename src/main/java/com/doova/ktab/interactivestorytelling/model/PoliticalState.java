package com.doova.ktab.interactivestorytelling.model;

import com.doova.ktab.interactivestorytelling.enums.MoralWeight;
import com.doova.ktab.interactivestorytelling.enums.Visibility;

public final class PoliticalState implements SessionState {

    private final int turnCount;
    private final Visibility visibility;
    private final MoralWeight moralWeight;
    private final int trustWithPeople;
    private final int trustWithAuthority;

    public PoliticalState() {
        this(0, Visibility.PRIVATE, MoralWeight.CLEAN, 0, 0);
    }

    public PoliticalState(int turnCount,
                          Visibility visibility,
                          MoralWeight moralWeight,
                          int trustWithPeople,
                          int trustWithAuthority) {
        this.turnCount = turnCount;
        this.visibility = visibility;
        this.moralWeight = moralWeight;
        this.trustWithPeople = trustWithPeople;
        this.trustWithAuthority = trustWithAuthority;
    }

    public static PoliticalState initial() { return new PoliticalState(); }

    @Override public int turnCount() { return turnCount; }
    public Visibility visibility() { return visibility; }
    public MoralWeight moralWeight() { return moralWeight; }
    public int trustWithPeople() { return trustWithPeople; }
    public int trustWithAuthority() { return trustWithAuthority; }

    public PoliticalState nextTurn() {
        return new PoliticalState(turnCount + 1, visibility, moralWeight, trustWithPeople, trustWithAuthority);
    }

    public PoliticalState withVisibility(Visibility v) {
        return new PoliticalState(turnCount, v, moralWeight, trustWithPeople, trustWithAuthority);
    }

    public PoliticalState withMoral(MoralWeight m) {
        return new PoliticalState(turnCount, visibility, m, trustWithPeople, trustWithAuthority);
    }

    public PoliticalState adjustTrust(int peopleDelta, int authorityDelta) {
        return new PoliticalState(turnCount, visibility, moralWeight,
                trustWithPeople + peopleDelta,
                trustWithAuthority + authorityDelta);
    }
}
