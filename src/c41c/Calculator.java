/*
 * Copyright (C) 2017-2019 Miloš Stojanović
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */

package c41c;

import java.math.BigInteger;

public class Calculator {
    private final Window Window;
    private final BigInteger[] Operands = new BigInteger[3];
    private int Base = 10;
    BigInteger CurrentMaxInt;
    private String Operation = "";

    // Operand indexes
    private static final int MEMORY = 2;
    private int Active = 0;
    private int Changing = 0;

    // Calculator flags
    private boolean OperationUnderway = false;
    private boolean DivisionByZero = false;
    private boolean NewRound = true;
    private boolean SecondOperandEntered = false;
    private boolean Signed;

    public Calculator(Window window, boolean signed) {
        Operands[0] = BigInteger.ZERO;
        Operands[1] = BigInteger.ZERO;
        Operands[MEMORY] = BigInteger.ZERO;

        Window = window;
        Signed = signed;
    }

    public String getFirstOperand() {
        return Operands[0].toString(Base);
    }

    public String getSecondOperand() {
        return Operands[1].toString(Base);
    }

    public BigInteger getActiveOperand() {
        return Operands[Active];
    }

    public String getOperation() {
        return Operation;
    }

    public boolean isOperationUnderway() {
        return OperationUnderway;
    }

    public void setActiveOperand(String number) {
        Operands[Active] = new BigInteger(number, Base);
        if (Active == 1)
            SecondOperandEntered = true;
        Window.changeAllBits(Operands[Active]);
    }

    public void setBase(int base) {
        Base = base;

        Window.refreshTextArea();
    }

    public void setSigned(boolean signed) {
        Signed = signed;

        Operands[0] = adjustForOverflow(Operands[0]);
        if (OperationUnderway)
            Operands[1] = adjustForOverflow(Operands[1]);

        Window.refreshTextArea();
    }

    public void flipBit(int bitPos) {
        Operands[Changing] = Operands[Changing].flipBit(bitPos);
        Operands[Changing] = adjustForOverflow(Operands[Changing]);
    }

    public void changeNumberOfBits(int bitNum) {
        if (bitNum < 0)
            return;
        else
            CurrentMaxInt = BigInteger.ONE.shiftLeft(bitNum-1);

        if (bitNum != 0)
            Operands[0] = Operands[0].mod(CurrentMaxInt.shiftLeft(1));
        else
            Operands[0] = BigInteger.ZERO;
        Operands[0] = adjustForOverflow(Operands[0]);
        Window.changeAllBits(Operands[0]);
        if (OperationUnderway) {
            if (bitNum != 0)
                Operands[1] = Operands[1].mod(CurrentMaxInt.shiftLeft(1));
            else
                Operands[1] = BigInteger.ZERO;
            Operands[1] = adjustForOverflow(Operands[1]);
            Window.changeAllBits(Operands[1]);
        }

        Window.refreshTextArea();
    }

    public void addDigit(String digit) {
        if (DivisionByZero)
            return;

        if (NewRound) {
            NewRound = false;
            Operands[Active] = BigInteger.ZERO;
            if (Active == 1)
                SecondOperandEntered = true;
        }

        BigInteger newValue = Operands[Active].multiply(new BigInteger(Integer.toString(Base)))
                                              .add(new BigInteger(digit, Base));

        if (newValue.compareTo(adjustForOverflow(newValue)) == 0) {
            Operands[Active] = newValue;
            Window.changeAllBits(Operands[Active]);
        }

        Window.refreshTextArea();
    }

    public void removeDigit() {
        if (DivisionByZero)
            return;
        if (OperationUnderway && !SecondOperandEntered)
            return;

        Operands[Active] = Operands[Active].divide(new BigInteger(Integer.toString(Base)));
        Window.changeAllBits(Operands[Active]);
        Window.refreshTextArea();
    }

    public void clearEntry() {
        Operands[Active] = BigInteger.ZERO;
        Window.changeAllBits(Operands[Active]);
        DivisionByZero = false;

        Window.refreshTextArea();
    }

    public void clear() {
        Operands[0] = BigInteger.ZERO;
        Operands[1] = BigInteger.ZERO;
        Operation = "";
        performOperation();
        Window.changeAllBits(Operands[Active]);
        DivisionByZero = false;

        Window.refreshTextArea();
    }

    public void plusMinus() {
        if (Operands[Changing].compareTo(CurrentMaxInt.negate()) != 0) {
            Operands[Changing] = Operands[Changing].negate();
            Window.changeAllBits(Operands[Changing]);

            Window.refreshTextArea();
        }
    }

    public void max() {
        Operands[Changing] = Signed ? CurrentMaxInt : CurrentMaxInt.shiftLeft(1);
        Operands[Changing] = Operands[Changing].subtract(BigInteger.ONE);

        Window.changeAllBits(Operands[Changing]);
        Window.refreshTextArea();
    }

    public void min() {
        Operands[Changing] = Signed ? CurrentMaxInt.negate() : BigInteger.ZERO;

        Window.changeAllBits(Operands[Changing]);
        Window.refreshTextArea();
    }

    public void not() {
        Operands[Changing] = adjustForOverflow(Operands[Changing].not());

        Window.changeAllBits(Operands[Changing]);
        Window.refreshTextArea();
    }

    public void MS() {
        Operands[MEMORY] = Operands[Changing];

        if (Operands[MEMORY].compareTo(BigInteger.ZERO) != 0)
            Window.setMLabel();
        else
            Window.clearMLabel();
    }

    public void MR() {
        Operands[Active] = adjustForOverflow(Operands[MEMORY]);

        Window.changeAllBits(Operands[Active]);
        Window.refreshTextArea();
    }

    public void MC() {
        Operands[MEMORY] = BigInteger.ZERO;

        Window.clearMLabel();
    }

    public void MArithmetic(String name) {
        switch(name) {
            case "+":
                Operands[MEMORY] = Operands[MEMORY].add(Operands[Changing]);
                break;
            case "-":
                Operands[MEMORY] = Operands[MEMORY].subtract(Operands[Changing]);
                break;
            default:
                return;
        }
        Operands[MEMORY] = adjustForOverflow(Operands[MEMORY]);

        if (Operands[MEMORY].compareTo(BigInteger.ZERO) != 0)
            Window.setMLabel();
        else
            Window.clearMLabel();
    }

    public void enterOperation(String operation) {
        String PreviousOperation = Operation;

        if (DivisionByZero)
            return;
        if (SecondOperandEntered)
            performOperation();

        Operation = operation;
        switch (Operation) {
            case "asr":
            case "shl":
            case "lsr":
            case "rol":
            case "ror":
                Operands[1] = BigInteger.ONE;
                if (PreviousOperation == Operation && OperationUnderway)
                    performOperation();
                Changing = 0;
                break;
            default:
                Operands[1] = Operands[0];
                Changing = 1;
                break;
        }
        NewRound = true;
        OperationUnderway = true;
        Active = 1;

        Window.refreshTextArea();
    }

    public void performOperation() {
        NewRound = true;
        SecondOperandEntered = false;
        OperationUnderway = false;
        Active = 0;
        Changing = 0;
        boolean signed = Signed;

        switch(Operation) {
            case "+":
                Operands[0] = Operands[0].add(Operands[1]);
                break;
            case "-":
                Operands[0] = Operands[0].subtract(Operands[1]);
                break;
            case "*":
                Operands[0] = Operands[0].multiply(Operands[1]);
                break;
            case "/":
                if (Operands[1].equals(BigInteger.ZERO)) {
                    Window.displayDivByZeroWarning();
                    DivisionByZero = true;
                    Operation = "";
                    return;
                }
                Operands[0] = Operands[0].divide(Operands[1]);
                break;
            case "mod":
                Operands[0] = Operands[0].mod(Operands[1]);
                break;
            case "and":
                Operands[0] = Operands[0].and(Operands[1]);
                break;
            case "or":
                Operands[0] = Operands[0].or(Operands[1]);
                break;
            case "xor":
                Operands[0] = Operands[0].xor(Operands[1]);
                break;
            case "asr":
                try {
                    Operands[0] = Operands[0].shiftRight(Operands[1].intValueExact());
                } catch (ArithmeticException e) {
                    Operands[0] = Operands[0].shiftRight(Operands[0].bitLength());
                    // If TOTAL_BIT_NUM ever gets above the maximum int value
                    // shifting will need a better implementation
                }
                break;
            case "lsr":
                Signed = false;
                Operands[0] = adjustForOverflow(Operands[0]);
                try {
                    Operands[0] = Operands[0].shiftRight(Operands[1].intValueExact());
                } catch (ArithmeticException e) {
                    Operands[0] = BigInteger.ZERO;
                }
                Signed = signed;
                break;
            case "shl":
                try {
                    Operands[0] = Operands[0].shiftLeft(Operands[1].intValueExact());
                } catch (ArithmeticException e) {
                    Operands[0] = BigInteger.ZERO;
                }
                break;
            case "ror":
                Signed = false;
                Operands[0] = adjustForOverflow(Operands[0]);
                int rotation_number = Operands[1].mod(new BigInteger(CurrentMaxInt.bitLength() + "")).intValue();
                for (int i = 0; i < rotation_number; i++) {
                    boolean bit = Operands[0].testBit(0);
                    Operands[0] = Operands[0].shiftRight(1);
                    if (bit)
                        Operands[0] = Operands[0].setBit(CurrentMaxInt.bitLength()-1);
                }
                Signed = signed;
                break;
            case "rol":
                Signed = false;
                Operands[0] = adjustForOverflow(Operands[0]);
                rotation_number = Operands[1].mod(new BigInteger(CurrentMaxInt.bitLength() + "")).intValue();
                for (int i = 0; i < rotation_number; i++) {
                    boolean bit = Operands[0].testBit(CurrentMaxInt.bitLength()-1);
                    Operands[0] = Operands[0].shiftLeft(1);
                    if (bit)
                        Operands[0] = Operands[0].setBit(0);
                }
                Signed = signed;
                break;
            default:
                return;
        }
        Operands[0] = adjustForOverflow(Operands[0]);
        Window.changeAllBits(Operands[0]);

        Window.refreshTextArea();
    }

    private BigInteger adjustForOverflow(BigInteger value) {
        value = value.mod(CurrentMaxInt.shiftLeft(1));
        BigInteger upperBound = Signed ? CurrentMaxInt : CurrentMaxInt.shiftLeft(1);
        BigInteger lowerBound = Signed ? CurrentMaxInt : BigInteger.ZERO;
        BigInteger overflow = value.subtract(upperBound);
        BigInteger underflow = value.add(lowerBound);

        if (overflow.compareTo(BigInteger.ZERO) >= 0)
            value = overflow.subtract(lowerBound);
        else if (underflow.compareTo(BigInteger.ZERO) < 0)
            value = underflow.add(upperBound);

        return value;
    }
}
