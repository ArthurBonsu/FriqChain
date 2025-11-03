package org.example.app.core.token;

/**
 * Defines token-related events
 */
public enum TokenEvent {
    /**
     * Emitted when tokens are transferred
     * Params: from (address), to (address), value (uint256)
     */
    TRANSFER("Transfer(address,address,uint256)"),
    
    /**
     * Emitted when approval is granted for token spending
     * Params: owner (address), spender (address), value (uint256)
     */
    APPROVAL("Approval(address,address,uint256)"),
    
    /**
     * Emitted when new tokens are minted
     * Params: to (address), amount (uint256)
     */
    MINT("Mint(address,uint256)"),
    
    /**
     * Emitted when tokens are burned
     * Params: from (address), amount (uint256)
     */
    BURN("Burn(address,uint256)");
    
    private final String signature;
    
    TokenEvent(String signature) {
        this.signature = signature;
    }
    
    /**
     * Gets the event signature
     */
    public String getSignature() {
        return signature;
    }
    
    /**
     * Gets an event by its signature
     */
    public static TokenEvent fromSignature(String signature) {
        for (TokenEvent event : values()) {
            if (event.signature.equals(signature)) {
                return event;
            }
        }
        return null;
    }
}