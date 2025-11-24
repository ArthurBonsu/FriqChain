package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * OfflineProofTransaction handles USSD/SMS-based offline settlements for
 * areas with poor connectivity. This is CRITICAL for the African Tokenized
 * Currency System's independence requirement:
 * 
 * "Enables financial independence in regions with poor connectivity or during
 * internet outages, ensuring the system remains operational even when disconnected.
 * Supports government-resistance by allowing transactions during internet shutdowns."
 * 
 * Layer 4 (Edge & Offline):
 * - USSD/SMS Gateways: Connect feature-phones via custodial micro-nodes
 * - Agent Nodes: Retail agents run lightweight clients
 * - Offline Transaction Certificates: Signed IOUs valid until next connectivity
 * - Community Mesh Nodes: Raspberry Pi-size nodes sync with regional hubs
 */
public class OfflineProofTransaction extends BaseTransaction {
    
    private final Address senderAddress;
    private final Address recipientAddress;
    private final Amount amount;
    private final String offlineChannel; // "USSD", "SMS", "QR", "NFC"
    private final byte[] offlineCertificate; // Cryptographic offline certificate
    private final long offlineTimestamp; // When offline tx was created
    private final long syncTimestamp; // When synced to blockchain
    private final String agentNodeId; // Agent/mesh node that facilitated
    private final List<byte[]> witnessSignatures; // Witness signatures for validation
    private final byte[] ussdSessionData; // USSD session metadata
    private final boolean requiresAgentSettlement; // If agent needs to settle
    private final BigInteger offlineNonce; // Nonce to prevent double-spending
    
    /**
     * Creates a new OfflineProofTransaction
     * 
     * @param senderAddress Sender's address
     * @param recipientAddress Recipient's address
     * @param amount Transaction amount
     * @param offlineChannel Channel used (USSD/SMS/QR/NFC)
     * @param offlineCertificate Signed offline certificate
     * @param offlineTimestamp When offline tx was created
     * @param agentNodeId Agent/mesh node ID
     * @param witnessSignatures Witness signatures
     * @param ussdSessionData USSD session metadata
     * @param requiresAgentSettlement If agent settlement needed
     * @param offlineNonce Offline transaction nonce
     */
    public OfflineProofTransaction(
            Address senderAddress, Address recipientAddress,
            Amount amount, String offlineChannel,
            byte[] offlineCertificate, long offlineTimestamp,
            String agentNodeId, List<byte[]> witnessSignatures,
            byte[] ussdSessionData, boolean requiresAgentSettlement,
            BigInteger offlineNonce) {
        
        super(buildTransactionData(senderAddress, recipientAddress, amount,
                offlineChannel, offlineCertificate, offlineTimestamp, agentNodeId,
                witnessSignatures, ussdSessionData, requiresAgentSettlement, offlineNonce),
                TransactionType.OFFLINE_PROOF);
        
        this.senderAddress = senderAddress;
        this.recipientAddress = recipientAddress;
        this.amount = amount;
        this.offlineChannel = offlineChannel.toUpperCase();
        this.offlineCertificate = Arrays.copyOf(offlineCertificate, offlineCertificate.length);
        this.offlineTimestamp = offlineTimestamp;
        this.syncTimestamp = System.currentTimeMillis();
        this.agentNodeId = agentNodeId;
        this.witnessSignatures = new ArrayList<>();
        for (byte[] sig : witnessSignatures) {
            this.witnessSignatures.add(Arrays.copyOf(sig, sig.length));
        }
        this.ussdSessionData = ussdSessionData != null ? 
            Arrays.copyOf(ussdSessionData, ussdSessionData.length) : new byte[0];
        this.requiresAgentSettlement = requiresAgentSettlement;
        this.offlineNonce = offlineNonce;
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            Address senderAddress, Address recipientAddress,
            Amount amount, String offlineChannel,
            byte[] offlineCertificate, long offlineTimestamp,
            String agentNodeId, List<byte[]> witnessSignatures,
            byte[] ussdSessionData, boolean requiresAgentSettlement,
            BigInteger offlineNonce) {
        
        ByteBuffer buffer = ByteBuffer.allocate(16384);
        
        // Write addresses
        buffer.put(senderAddress.toSlice());
        buffer.put(recipientAddress.toSlice());
        
        // Write amount
        byte[] amountBytes = amount.toBytes();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write offline channel
        byte[] channelBytes = offlineChannel.getBytes();
        buffer.putInt(channelBytes.length);
        buffer.put(channelBytes);
        
        // Write offline certificate
        buffer.putInt(offlineCertificate.length);
        buffer.put(offlineCertificate);
        
        // Write timestamps
        buffer.putLong(offlineTimestamp);
        
        // Write agent node ID
        byte[] agentBytes = agentNodeId.getBytes();
        buffer.putInt(agentBytes.length);
        buffer.put(agentBytes);
        
        // Write witness signatures
        buffer.putInt(witnessSignatures.size());
        for (byte[] sig : witnessSignatures) {
            buffer.putInt(sig.length);
            buffer.put(sig);
        }
        
        // Write USSD session data
        if (ussdSessionData != null) {
            buffer.putInt(ussdSessionData.length);
            buffer.put(ussdSessionData);
        } else {
            buffer.putInt(0);
        }
        
        // Write agent settlement flag
        buffer.put((byte) (requiresAgentSettlement ? 1 : 0));
        
        // Write offline nonce
        byte[] nonceBytes = offlineNonce.toByteArray();
        buffer.putInt(nonceBytes.length);
        buffer.put(nonceBytes);
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }
    
    @Override
    public Address getRecipientAddress() {
        return recipientAddress;
    }
    
    @Override
    public Amount getAmount() {
        return amount;
    }
    
    public String getOfflineChannel() {
        return offlineChannel;
    }
    
    public byte[] getOfflineCertificate() {
        return Arrays.copyOf(offlineCertificate, offlineCertificate.length);
    }
    
    public long getOfflineTimestamp() {
        return offlineTimestamp;
    }
    
    public long getSyncTimestamp() {
        return syncTimestamp;
    }
    
    public String getAgentNodeId() {
        return agentNodeId;
    }
    
    public List<byte[]> getWitnessSignatures() {
        List<byte[]> copies = new ArrayList<>();
        for (byte[] sig : witnessSignatures) {
            copies.add(Arrays.copyOf(sig, sig.length));
        }
        return copies;
    }
    
    public byte[] getUssdSessionData() {
        return Arrays.copyOf(ussdSessionData, ussdSessionData.length);
    }
    
    public boolean requiresAgentSettlement() {
        return requiresAgentSettlement;
    }
    
    public BigInteger getOfflineNonce() {
        return offlineNonce;
    }
    
    /**
     * Gets the offline duration (time between offline creation and sync)
     */
    public long getOfflineDuration() {
        return syncTimestamp - offlineTimestamp;
    }
    
    @Override
    public boolean supportsOfflineProcessing() {
        return true; // This IS an offline transaction
    }
    
    @Override
    protected boolean validateTransactionRules() {
        // Validate offline channel
        if (!isValidOfflineChannel(offlineChannel)) {
            return false;
        }
        
        // Validate offline certificate
        if (offlineCertificate == null || offlineCertificate.length == 0) {
            return false;
        }
        
        // Validate timestamps
        if (offlineTimestamp <= 0 || syncTimestamp <= 0 || 
            syncTimestamp < offlineTimestamp) {
            return false;
        }
        
        // Check offline duration (max 72 hours)
        long maxOfflineDuration = 72 * 60 * 60 * 1000L; // 72 hours
        if (getOfflineDuration() > maxOfflineDuration) {
            return false;
        }
        
        // Validate agent node ID
        if (agentNodeId == null || agentNodeId.isEmpty()) {
            return false;
        }
        
        // Require minimum witness signatures based on amount
        int requiredWitnesses = getRequiredWitnessCount();
        if (witnessSignatures.size() < requiredWitnesses) {
            return false;
        }
        
        // Verify offline certificate
        if (!verifyOfflineCertificate()) {
            return false;
        }
        
        // Verify witness signatures
        if (!verifyWitnessSignatures()) {
            return false;
        }
        
        // Check for double-spending
        return !isDoubleSpent();
    }
    
    /**
     * Checks if offline channel is valid
     */
    private boolean isValidOfflineChannel(String channel) {
        String[] validChannels = {"USSD", "SMS", "QR", "NFC", "BLUETOOTH", "MESH"};
        for (String valid : validChannels) {
            if (channel.equals(valid)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets required witness count based on transaction amount
     */
    private int getRequiredWitnessCount() {
        BigInteger threshold = BigInteger.valueOf(10000); // 10,000 tokens
        
        if (amount.getValue().compareTo(threshold) > 0) {
            return 2; // High-value: 2 witnesses
        } else {
            return 1; // Standard: 1 witness
        }
    }
    
    /**
     * Verifies the offline certificate authenticity
     */
    private boolean verifyOfflineCertificate() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(offlineCertificate);
            
            // Read certificate version
            byte version = buffer.get();
            if (version != 0x01) {
                return false;
            }
            
            // Read certificate type
            byte certType = buffer.get();
            // 0x01 = IOU certificate
            // 0x02 = Agent-backed certificate
            // 0x03 = Mesh-verified certificate
            
            if (certType < 0x01 || certType > 0x03) {
                return false;
            }
            
            // Read issuer signature
            int sigLength = buffer.getInt();
            byte[] issuerSignature = new byte[sigLength];
            buffer.get(issuerSignature);
            
            // Read transaction hash
            byte[] txHash = new byte[32];
            buffer.get(txHash);
            
            // Read expiry timestamp
            long expiry = buffer.getLong();
            
            // Check certificate hasn't expired
            if (syncTimestamp > expiry) {
                return false; // Certificate expired
            }
            
            // Verify issuer signature
            // In production: verify against registered agent/node public key
            if (issuerSignature.length < 64) {
                return false;
            }
            
            // Verify transaction hash matches
            byte[] computedHash = computeTransactionHash();
            if (!Arrays.equals(txHash, computedHash)) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Computes transaction hash for verification
     */
    private byte[] computeTransactionHash() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.put(senderAddress.toSlice());
            buffer.put(recipientAddress.toSlice());
            buffer.put(amount.toBytes());
            buffer.putLong(offlineTimestamp);
            buffer.put(offlineNonce.toByteArray());
            
            buffer.flip();
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            
            return digest.digest(data);
            
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    /**
     * Verifies witness signatures
     */
    private boolean verifyWitnessSignatures() {
        int validSignatures = 0;
        
        for (byte[] signature : witnessSignatures) {
            if (verifyWitnessSignature(signature)) {
                validSignatures++;
            }
        }
        
        return validSignatures >= getRequiredWitnessCount();
    }
    
    /**
     * Verifies a single witness signature
     */
    private boolean verifyWitnessSignature(byte[] signature) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(signature);
            
            // Read witness ID
            int witnessIdLength = buffer.getInt();
            byte[] witnessId = new byte[witnessIdLength];
            buffer.get(witnessId);
            
            // Read signature data
            int sigLength = buffer.getInt();
            byte[] sigData = new byte[sigLength];
            buffer.get(sigData);
            
            // Read timestamp
            long timestamp = buffer.getLong();
            
            // Verify signature is timely
            if (Math.abs(timestamp - offlineTimestamp) > 60000) { // Within 1 minute
                return false;
            }
            
            // In production: verify signature against witness public key
            return sigData.length >= 64;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if this transaction has been double-spent
     */
    private boolean isDoubleSpent() {
        // In production: check against a registry of used offline nonces
        // For now, we'll do basic validation
        
        // Check nonce is not zero
        if (offlineNonce.equals(BigInteger.ZERO)) {
            return true;
        }
        
        // In production: query database
        // return offlineNonceRegistry.hasBeenUsed(senderAddress, offlineNonce);
        
        return false; // Assume not double-spent for validation
    }
    
    /**
     * Parses USSD session data
     */
    public Map<String, Object> parseUssdSession() {
        Map<String, Object> session = new HashMap<>();
        
        if (ussdSessionData.length == 0) {
            return session;
        }
        
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ussdSessionData);
            
            // Read session ID
            int sessionIdLength = buffer.getInt();
            byte[] sessionId = new byte[sessionIdLength];
            buffer.get(sessionId);
            session.put("sessionId", new String(sessionId));
            
            // Read phone number
            int phoneLength = buffer.getInt();
            byte[] phone = new byte[phoneLength];
            buffer.get(phone);
            session.put("phoneNumber", new String(phone));
            
            // Read USSD menu path
            int pathLength = buffer.getInt();
            byte[] path = new byte[pathLength];
            buffer.get(path);
            session.put("menuPath", new String(path));
            
            // Read operator
            int operatorLength = buffer.getInt();
            byte[] operator = new byte[operatorLength];
            buffer.get(operator);
            session.put("mobileOperator", new String(operator));
            
            return session;
            
        } catch (Exception e) {
            return session;
        }
    }
    
    /**
     * Formats transaction for USSD display (160 char limit)
     */
    public String formatForUssdDisplay() {
        return String.format("Transfer: %s %s to %s. Fee: %s. Confirm?",
            amount.toDecimalString(),
            amount.getCurrency(),
            recipientAddress.toString().substring(0, 8) + "...",
            getRequiredFee().toDecimalString()
        );
    }
    
    /**
     * Formats transaction for SMS (160 char limit)
     */
    public String formatForSmsNotification() {
        return String.format("TINC: You sent %s %s. Tx ID: %s. Synced at %s",
            amount.toDecimalString(),
            amount.getCurrency(),
            getTransactionId().substring(0, 8),
            new Date(syncTimestamp).toString()
        );
    }
    
    @Override
    public Amount getRequiredFee() {
        // Offline transactions have subsidized fees
        BigInteger baseFee = amount.getValue().divide(BigInteger.valueOf(1000)); // 0.1%
        
        // Additional agent fee if agent settlement required
        if (requiresAgentSettlement) {
            BigInteger agentFee = BigInteger.valueOf(10); // Flat agent fee
            baseFee = baseFee.add(agentFee);
        }
        
        return new Amount(baseFee, amount.getCurrency(), amount.getDecimals());
    }
    
    /**
     * Gets the offline sync delay
     */
    public String getOfflineSyncDelay() {
        long delayMs = getOfflineDuration();
        
        if (delayMs < 60000) {
            return String.format("%d seconds", delayMs / 1000);
        } else if (delayMs < 3600000) {
            return String.format("%d minutes", delayMs / 60000);
        } else {
            return String.format("%d hours", delayMs / 3600000);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("Offline %s: %s via %s (synced after %s)",
            offlineChannel,
            amount.toFormattedString(),
            agentNodeId,
            getOfflineSyncDelay()
        );
    }
    
    @Override
    public String toString() {
        return String.format("OfflineProofTransaction{channel='%s', amount=%s, agent='%s', offline=%dms}",
            offlineChannel, amount, agentNodeId, getOfflineDuration());
    }
}