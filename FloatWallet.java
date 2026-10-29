
public class FloatWallet {
    protected String walletName;
    protected double balance;

    public FloatWallet(String walletName, double balance) {
        this.walletName = walletName;
        this.balance = balance;
    }

    public String getWalletName() {
        return walletName;
    }

    public double getBalance() {
        return balance;
    }

    public void addFloat(double amount) {
        balance += amount;
    }

    public void displayBalance() {
        System.out.println(walletName + " Balance: K" + balance);
    }
}
