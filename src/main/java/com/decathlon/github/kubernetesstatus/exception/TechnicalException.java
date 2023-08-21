package com.decathlon.github.kubernetesstatus.exception;

public class TechnicalException extends RuntimeException{
    public TechnicalException(Exception e){
        super(e);
    }

    public TechnicalException(String msg){
        super(msg);
    }
}
