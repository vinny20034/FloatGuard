package com.floatguard;
public class EFloat extends FloatWallet {

    public EFloat(double balance) {
        super("E-Float", balance);
    }

    @Override
    public void displayBalance() {
        System.out.println("E-Float Balance: K" + balance);
    }
}
