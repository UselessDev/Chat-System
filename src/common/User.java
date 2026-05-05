package common;

import java.io.Serializable;

/**
 * Represents a connected user. Sent to clients to populate user lists.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String status; // "Online", "Away", etc.
    
    public User(String username) {
        this.username = username;
        this.status = "Online";
    }
    
    public String getUsername() { return username; }
    public String getStatus() { return status; }
    
    @Override
    public String toString() { return username; }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof User) {
            return username.equals(((User) obj).username);
        }
        return false;
    }
    
    @Override
    public int hashCode() { return username.hashCode(); }
}