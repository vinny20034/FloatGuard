package com.floatguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Agent {

    private final int id;
    private String name;
    private String location;
    private final List<FloatWallet> wallets = new ArrayList<>();

    public Agent(int id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }

    public void addWallet(FloatWallet wallet) {
        wallets.add(wallet);
    }

    public List<FloatWallet> getWallets() {
        return Collections.unmodifiableList(wallets);
    }
}
