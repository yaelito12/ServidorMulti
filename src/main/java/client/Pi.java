package client;

import compute.Task;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Pi implements Task<BigDecimal>, Serializable {

    private static final long serialVersionUID = 227L;
    private final int digits;

    public Pi(int digits) {
        this.digits = digits;
    }

    @Override
    public BigDecimal execute() {
        return computePi(digits);
    }

    public static BigDecimal computePi(int digits) {
        int scale = digits + 5;
        BigDecimal arctan1_5 = arctan(5, scale);
        BigDecimal arctan1_239 = arctan(239, scale);
        BigDecimal pi = arctan1_5.multiply(new BigDecimal(4)).subtract(
                arctan1_239).multiply(new BigDecimal(4));
        return pi.setScale(digits, RoundingMode.HALF_UP);
    }

    public static BigDecimal arctan(int invk, int scale) {
        BigDecimal result, numer, term;
        BigDecimal invk2 = BigDecimal.valueOf(invk * invk);

        numer = BigDecimal.ONE.divide(BigDecimal.valueOf(invk),
                scale, RoundingMode.HALF_EVEN);

        result = numer;
        int i = 1;
        do {
            numer = numer.divide(invk2, scale, RoundingMode.HALF_EVEN);
            int denom = 2 * i + 1;
            term = numer.divide(BigDecimal.valueOf(denom), scale, 
                    RoundingMode.HALF_EVEN);
            if ((i % 2) != 0) {
                result = result.subtract(term);
            } else {
                result = result.add(term);
            }
            i++;
        } while (term.compareTo(BigDecimal.ZERO) != 0);
        return result;
    }
}
