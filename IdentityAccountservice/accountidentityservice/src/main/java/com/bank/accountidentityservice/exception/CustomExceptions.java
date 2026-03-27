package com.bank.accountidentityservice.exception;

public class CustomExceptions {

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String message) { super(message); }
    }

    public static class InvalidOtpException extends RuntimeException {
        public InvalidOtpException(String message) { super(message); }
    }

    public static class OtpExpiredException extends RuntimeException {
        public OtpExpiredException(String message) { super(message); }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) { super(message); }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String message) { super(message); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) { super(message); }
    }

    public static class AccountLockedOrDisabledException extends RuntimeException {
        public AccountLockedOrDisabledException(String message) { super(message); }
    }

    public static class PinMismatchException extends RuntimeException {
        public PinMismatchException(String message) { super(message); }
    }

    public static class DuplicateAccountTypeException extends RuntimeException {
        public DuplicateAccountTypeException(String message) { super(message); }
    }
}
