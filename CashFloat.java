
public class CashFloat extends FloatWallet {

    public CashFloat(double balance) {
        super("Cash Float", balance);
    }

    @Override
    public void displayBalance() {
        System.out.println("Cash Float Balance: K" + balance);
    }
}
