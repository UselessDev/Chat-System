package common;

import java.io.Serializable;

/**
 * First object sent after TCP connect. Server validates password hash.
 * If {@code registerNewAccount} is true, a new account is created when the username is unused.
 */
public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final boolean registerNewAccount;

    public LoginRequest(String username, String password, boolean registerNewAccount) {
        this.username = username;
        this.password = password != null ? password : "";
        this.registerNewAccount = registerNewAccount;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isRegisterNewAccount() { return registerNewAccount; }
}
