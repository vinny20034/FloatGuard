package com.floatguard;

public class Main {
    public static void main(String[] args) {

        CashFloat cash = new CashFloat(50000);
        EFloat electronic = new EFloat(10000);

        cash.displayBalance();
        electronic.displayBalance();

        cash.addFloat(1500);
        electronic.addFloat(2000);

        System.out.println("After adding float:");

        cash.displayBalance();
        electronic.displayBalance();
    }
}
