package org.example.app.core.banking.bridge;

import java.util.stream.Collectors;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * BankAPIEncryptedChannel - Secure encrypted communication channel for banks
 * 
 * This system provides government-resistant encrypted channels for bank API
 * communication, ensuring that no single authority can intercept or block
 * communication between the blockchain and registered banks.
 */
public class BankAPIEncryptedChannel {
    private static final Logger LOGGER = Logger.getLogger(BankAPIEncryptedChannel.class.getName());
    
    // Encryption algorithms and settings
    private static final String SYMMETRIC_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ASYMMETRIC_ALGORITHM = "RSA/OAEP/SHA-256";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final int AES_KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 96; // 12 bytes
    private static final int GCM_TAG_LENGTH = 128; // 16 bytes
    private static final int RSA_KEY_LENGTH = 4096;
    
    // Channel security settings
    private static final long CHANNEL_EXPIRY_HOURS = 24; // 24-hour channel expiry
    private static final long KEY_ROTATION_HOURS = 6;    // 6-hour key rotation
    private static final int MAX_MESSAGE_SIZE = 1048576; // 1MB max message size
    private static final int MAX_CHANNELS_PER_BANK = 10;
    private static final String PROTOCOL_VERSION = "TINC_SECURE_v1.0";
    
    // Channel states
    public enum ChannelState {
        INITIALIZING,    // Channel being established
        ACTIVE,          // Channel ready for communication
        KEY_ROTATION,    // Rotating encryption keys
        EXPIRED,         // Channel has expired
        SUSPENDED,       // Channel temporarily suspended
        TERMINATED       // Channel permanently closed
    }
    
    // Message types
    public enum MessageType {
        HANDSHAKE_INIT,     // Initial handshake
        HANDSHAKE_RESPONSE, // Handshake response
        KEY_EXCHANGE,       // Key exchange message
        ENCRYPTED_DATA,     // Regular encrypted data
        HEARTBEAT,          // Keep-alive message
        ERROR_RESPONSE,     // Error message
        DISCONNECT         // Disconnect notification
    }
    
    /**
     * Represents an encrypted communication channel with a bank
     */
    public static class SecureChannel {
        private final String channelId;
        private final String bankId;
        private final Address bankAddress;
        private final Date establishedAt;
        private final KeyPair channelKeyPair;
        private final Certificate bankCertificate;
        
        private ChannelState state;
        private SecretKey symmetricKey;
        private PublicKey bankPublicKey;
        private byte[] sessionSalt;
        private Date lastKeyRotation;
        private Date lastActivity;
        private long messageCounter;
        private final Map<String, Object> channelMetadata;
        
        public SecureChannel(String bankId, Address bankAddress, Certificate bankCertificate) {
            this.channelId = UUID.randomUUID().toString();
            this.bankId = bankId;
            this.bankAddress = bankAddress;
            this.bankCertificate = bankCertificate;
            this.establishedAt = new Date();
            this.channelKeyPair = generateChannelKeyPair();
            this.state = ChannelState.INITIALIZING;
            this.channelMetadata = new HashMap<>();
            this.messageCounter = 0;
            this.sessionSalt = generateSessionSalt();
            this.lastActivity = new Date();
            
            if (bankCertificate != null) {
                this.bankPublicKey = bankCertificate.getPublicKey();
            }
        }
        
        // Getters
        public String getChannelId() { return channelId; }
        public String getBankId() { return bankId; }
        public Address getBankAddress() { return bankAddress; }
        public Date getEstablishedAt() { return establishedAt; }
        public KeyPair getChannelKeyPair() { return channelKeyPair; }
        public Certificate getBankCertificate() { return bankCertificate; }
        public ChannelState getState() { return state; }
        public SecretKey getSymmetricKey() { return symmetricKey; }
        public PublicKey getBankPublicKey() { return bankPublicKey; }
        public Date getLastKeyRotation() { return lastKeyRotation; }
        public Date getLastActivity() { return lastActivity; }
        public long getMessageCounter() { return messageCounter; }
        public Map<String, Object> getChannelMetadata() { return new HashMap<>(channelMetadata); }
        
        // Setters
        public void setState(ChannelState state) { this.state = state; }
        public void setSymmetricKey(SecretKey key) { this.symmetricKey = key; }
        public void setBankPublicKey(PublicKey key) { this.bankPublicKey = key; }
        public void updateLastActivity() { this.lastActivity = new Date(); }
        public void incrementMessageCounter() { this.messageCounter++; }
        
        public void addMetadata(String key, Object value) {
            channelMetadata.put(key, value);
        }
        
        public boolean isExpired() {
            long ageHours = ChronoUnit.HOURS.between(establishedAt.toInstant(), Instant.now());
            return ageHours >= CHANNEL_EXPIRY_HOURS;
        }
        
        public boolean needsKeyRotation() {
            if (lastKeyRotation == null) {
                return true;
            }
            long hoursSinceRotation = ChronoUnit.HOURS.between(lastKeyRotation.toInstant(), Instant.now());
            return hoursSinceRotation >= KEY_ROTATION_HOURS;
        }
        
        public void markKeyRotated() {
            this.lastKeyRotation = new Date();
        }
        
        private KeyPair generateChannelKeyPair() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
                generator.initialize(256);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                LOGGER.severe("Error generating channel key pair: " + e.getMessage());
                return null;
            }
        }
        
        private byte[] generateSessionSalt() {
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            return salt;
        }
        
        public byte[] getSessionSalt() {
            return Arrays.copyOf(sessionSalt, sessionSalt.length);
        }
        
        public void regenerateSessionSalt() {
            this.sessionSalt = generateSessionSalt();
        }
    }
    
    /**
     * Represents an encrypted message
     */
    public static class EncryptedMessage {
        private final String messageId;
        private final String channelId;
        private final MessageType type;
        private final byte[] encryptedData;
        private final byte[] iv;
        private final byte[] authTag;
        private final byte[] signature;
        private final Date timestamp;
        private final Map<String, String> headers;
        
        public EncryptedMessage(String channelId, MessageType type, byte[] encryptedData,
                               byte[] iv, byte[] authTag, byte[] signature) {
            this.messageId = UUID.randomUUID().toString();
            this.channelId = channelId;
            this.type = type;
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.authTag = authTag;
            this.signature = signature;
            this.timestamp = new Date();
            this.headers = new HashMap<>();
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public String getChannelId() { return channelId; }
        public MessageType getType() { return type; }
        public byte[] getEncryptedData() { return Arrays.copyOf(encryptedData, encryptedData.length); }
        public byte[] getIv() { return Arrays.copyOf(iv, iv.length); }
        public byte[] getAuthTag() { return Arrays.copyOf(authTag, authTag.length); }
        public byte[] getSignature() { return Arrays.copyOf(signature, signature.length); }
        public Date getTimestamp() { return timestamp; }
        public Map<String, String> getHeaders() { return new HashMap<>(headers); }
        
        public void addHeader(String key, String value) {
            headers.put(key, value);
        }
        
        public boolean isExpired() {
            long ageMinutes = ChronoUnit.MINUTES.between(timestamp.toInstant(), Instant.now());
            return ageMinutes > 30; // 30 minute message expiry
        }
        
        public byte[] serialize() {
            // In a real implementation, this would use proper serialization
            // For now, combine all components
            StringBuilder sb = new StringBuilder();
            sb.append(messageId).append("|");
            sb.append(channelId).append("|");
            sb.append(type).append("|");
            sb.append(Base64.getEncoder().encodeToString(encryptedData)).append("|");
            sb.append(Base64.getEncoder().encodeToString(iv)).append("|");
            sb.append(Base64.getEncoder().encodeToString(authTag)).append("|");
            sb.append(Base64.getEncoder().encodeToString(signature));
            
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        
        public static EncryptedMessage deserialize(byte[] data) {
            // In a real implementation, this would properly deserialize
            // For now, return a placeholder
            return null;
        }
    }
    
    // Core infrastructure
    private final PKIManager pkiManager;
    private final CertificateAuthority certificateAuthority;
    private final EventLog eventLog;
    private final BlockchainConfig config;
    
    // Channel management
    private final Map<String, SecureChannel> activeChannels;      // channelId -> channel
    private final Map<String, List<String>> bankChannels;        // bankId -> list of channel IDs
    private final Map<String, EncryptedMessage> pendingMessages; // messageId -> message
    private final AtomicLong totalMessages;
    private final AtomicLong totalChannels;
    
    // Cryptographic components
    private final SecureRandom secureRandom;
    private final KeyPair masterKeyPair;
    
    /**
     * Constructor
     */
    public BankAPIEncryptedChannel(PKIManager pkiManager,
                                   CertificateAuthority certificateAuthority,
                                   EventLog eventLog,
                                   BlockchainConfig config) {
        this.pkiManager = pkiManager;
        this.certificateAuthority = certificateAuthority;
        this.eventLog = eventLog;
        this.config = config;
        
        this.activeChannels = new ConcurrentHashMap<>();
        this.bankChannels = new ConcurrentHashMap<>();
        this.pendingMessages = new ConcurrentHashMap<>();
        this.totalMessages = new AtomicLong(0);
        this.totalChannels = new AtomicLong(0);
        
        this.secureRandom = new SecureRandom();
        this.masterKeyPair = generateMasterKeyPair();
        
        LOGGER.info("BankAPIEncryptedChannel initialized");
    }
    
    /**
     * Establishes a secure channel with a bank
     */
    public String establishChannel(String bankId, Address bankAddress, Certificate bankCertificate) {
        try {
            LOGGER.info("Establishing secure channel with bank: " + bankId);
            
            // Validate bank certificate
            if (!validateBankCertificate(bankCertificate)) {
                LOGGER.warning("Invalid bank certificate for: " + bankId);
                return null;
            }
            
            // Check channel limits
            if (!checkChannelLimits(bankId)) {
                LOGGER.warning("Channel limit exceeded for bank: " + bankId);
                return null;
            }
            
            // Create secure channel
            SecureChannel channel = new SecureChannel(bankId, bankAddress, bankCertificate);
            
            // Perform key exchange
            if (!performKeyExchange(channel)) {
                LOGGER.warning("Key exchange failed for bank: " + bankId);
                return null;
            }
            
            // Activate channel
            channel.setState(ChannelState.ACTIVE);
            activeChannels.put(channel.getChannelId(), channel);
            
            // Update bank mapping
            bankChannels.computeIfAbsent(bankId, k -> new ArrayList<>())
                       .add(channel.getChannelId());
            
            totalChannels.incrementAndGet();
            
            eventLog.logEvent("SECURE_CHANNEL_ESTABLISHED: " + bankId + 
                            " (Channel: " + channel.getChannelId() + ")");
            
            LOGGER.info("Secure channel established: " + channel.getChannelId());
            return channel.getChannelId();
            
        } catch (Exception e) {
            LOGGER.severe("Error establishing channel with bank " + bankId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sends an encrypted message through a channel
     */
    public boolean sendEncryptedMessage(String channelId, MessageType type, 
                                      byte[] plainData, Map<String, String> headers) {
        try {
            SecureChannel channel = activeChannels.get(channelId);
            if (channel == null || channel.getState() != ChannelState.ACTIVE) {
                LOGGER.warning("Channel not available: " + channelId);
                return false;
            }
            
            // Validate message size
            if (plainData.length > MAX_MESSAGE_SIZE) {
                LOGGER.warning("Message too large: " + plainData.length + " bytes");
                return false;
            }
            
            // Check if key rotation is needed
            if (channel.needsKeyRotation()) {
                if (!rotateChannelKeys(channel)) {
                    LOGGER.warning("Key rotation failed for channel: " + channelId);
                    return false;
                }
            }
            
            // Encrypt the message
            EncryptedMessage encryptedMsg = encryptMessage(channel, type, plainData);
            if (encryptedMsg == null) {
                LOGGER.warning("Message encryption failed");
                return false;
            }
            
            // Add headers
            if (headers != null) {
                headers.forEach(encryptedMsg::addHeader);
            }
            
            // Add protocol headers
            encryptedMsg.addHeader("Protocol-Version", PROTOCOL_VERSION);
            encryptedMsg.addHeader("Message-Counter", String.valueOf(channel.getMessageCounter()));
            encryptedMsg.addHeader("Timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Send message (in a real implementation, this would use network protocols)
            boolean sent = transmitMessage(encryptedMsg, channel);
            
            if (sent) {
                channel.updateLastActivity();
                channel.incrementMessageCounter();
                totalMessages.incrementAndGet();
                
                eventLog.logEvent("ENCRYPTED_MESSAGE_SENT: " + encryptedMsg.getMessageId() + 
                                " (Channel: " + channelId + ", Type: " + type + ")");
                
                LOGGER.info("Encrypted message sent: " + encryptedMsg.getMessageId());
                return true;
            } else {
                LOGGER.warning("Message transmission failed");
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error sending encrypted message: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Receives and decrypts a message
     */
    public byte[] receiveEncryptedMessage(String channelId, EncryptedMessage encryptedMsg) {
        try {
            SecureChannel channel = activeChannels.get(channelId);
            if (channel == null || channel.getState() != ChannelState.ACTIVE) {
                LOGGER.warning("Channel not available for receiving: " + channelId);
                return null;
            }
            
            // Validate message
            if (encryptedMsg.isExpired()) {
                LOGGER.warning("Message expired: " + encryptedMsg.getMessageId());
                return null;
            }
            
            // Verify message signature
            if (!verifyMessageSignature(encryptedMsg, channel)) {
                LOGGER.warning("Message signature verification failed");
                return null;
            }
            
            // Decrypt the message
            byte[] plainData = decryptMessage(encryptedMsg, channel);
            
            if (plainData != null) {
                channel.updateLastActivity();
                
                eventLog.logEvent("ENCRYPTED_MESSAGE_RECEIVED: " + encryptedMsg.getMessageId() + 
                                " (Channel: " + channelId + ")");
                
                LOGGER.info("Message decrypted: " + encryptedMsg.getMessageId());
            } else {
                LOGGER.warning("Message decryption failed: " + encryptedMsg.getMessageId());
            }
            
            return plainData;
            
        } catch (Exception e) {
            LOGGER.severe("Error receiving encrypted message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validates bank certificate
     */
    private boolean validateBankCertificate(Certificate bankCertificate) {
        if (bankCertificate == null) {
            return false;
        }
        
        try {
            // Verify certificate with CA
            boolean isValid = certificateAuthority.verifyCertificate(bankCertificate);
            
            if (!isValid) {
                LOGGER.warning("Bank certificate verification failed");
                return false;
            }
            
            // Additional validation (expiry, revocation, etc.) would go here
            
            return true;
            
        } catch (Exception e) {
            LOGGER.warning("Error validating bank certificate: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks channel limits for a bank
     */
    private boolean checkChannelLimits(String bankId) {
        List<String> existingChannels = bankChannels.get(bankId);
        
        if (existingChannels == null) {
            return true;
        }
        
        // Count active channels
        long activeCount = existingChannels.stream()
            .map(activeChannels::get)
            .filter(Objects::nonNull)
            .filter(channel -> channel.getState() == ChannelState.ACTIVE)
            .count();
        
        return activeCount < MAX_CHANNELS_PER_BANK;
    }
    
    /**
     * Performs key exchange to establish secure communication
     */
    private boolean performKeyExchange(SecureChannel channel) {
        try {
            // Generate shared secret using ECDH
            KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            keyAgreement.init(channel.getChannelKeyPair().getPrivate());
            keyAgreement.doPhase(channel.getBankPublicKey(), true);
            
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Derive symmetric key from shared secret
            SecretKey symmetricKey = deriveSymmetricKey(sharedSecret, channel.getSessionSalt());
            channel.setSymmetricKey(symmetricKey);
            channel.markKeyRotated();
            
            LOGGER.info("Key exchange completed for channel: " + channel.getChannelId());
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Key exchange failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Derives symmetric key from shared secret
     */
    private SecretKey deriveSymmetricKey(byte[] sharedSecret, byte[] salt) throws Exception {
        // Use PBKDF2 to derive AES key
        KeySpec spec = new PBEKeySpec(
            Base64.getEncoder().encodeToString(sharedSecret).toCharArray(),
            salt, 100000, AES_KEY_LENGTH);
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    /**
     * Rotates channel encryption keys
     */
    private boolean rotateChannelKeys(SecureChannel channel) {
        try {
            LOGGER.info("Rotating keys for channel: " + channel.getChannelId());
            
            channel.setState(ChannelState.KEY_ROTATION);
            
            // Generate new session salt
            channel.regenerateSessionSalt();
            
            // Perform key exchange again
            boolean success = performKeyExchange(channel);
            
            if (success) {
                channel.setState(ChannelState.ACTIVE);
                eventLog.logEvent("KEY_ROTATION_SUCCESS: " + channel.getChannelId());
                LOGGER.info("Key rotation successful: " + channel.getChannelId());
            } else {
                channel.setState(ChannelState.SUSPENDED);
                eventLog.logEvent("KEY_ROTATION_FAILED: " + channel.getChannelId());
                LOGGER.warning("Key rotation failed: " + channel.getChannelId());
            }
            
            return success;
            
        } catch (Exception e) {
            LOGGER.severe("Error rotating keys: " + e.getMessage());
            channel.setState(ChannelState.SUSPENDED);
            return false;
        }
    }
    
    /**
     * Encrypts a message
     */
    private EncryptedMessage encryptMessage(SecureChannel channel, MessageType type, byte[] plainData) {
        try {
            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH / 8];
            secureRandom.nextBytes(iv);
            
            // Encrypt data
            Cipher cipher = Cipher.getInstance(SYMMETRIC_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, channel.getSymmetricKey(), gcmSpec);
            
            // Add channel ID as additional authenticated data
            cipher.updateAAD(channel.getChannelId().getBytes(StandardCharsets.UTF_8));
            
            byte[] encryptedData = cipher.doFinal(plainData);
            
            // Extract auth tag (last 16 bytes for GCM)
            int ciphertextLength = encryptedData.length - (GCM_TAG_LENGTH / 8);
            byte[] ciphertext = Arrays.copyOf(encryptedData, ciphertextLength);
            byte[] authTag = Arrays.copyOfRange(encryptedData, ciphertextLength, encryptedData.length);
            
            // Create digital signature
            byte[] signature = createMessageSignature(ciphertext, channel);
            
            return new EncryptedMessage(channel.getChannelId(), type, ciphertext, iv, authTag, signature);
            
        } catch (Exception e) {
            LOGGER.severe("Error encrypting message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Decrypts a message
     */
    private byte[] decryptMessage(EncryptedMessage encryptedMsg, SecureChannel channel) {
        try {
            // Reconstruct encrypted data with auth tag
            byte[] ciphertext = encryptedMsg.getEncryptedData();
            byte[] authTag = encryptedMsg.getAuthTag();
            byte[] encryptedDataWithTag = new byte[ciphertext.length + authTag.length];
            
            System.arraycopy(ciphertext, 0, encryptedDataWithTag, 0, ciphertext.length);
            System.arraycopy(authTag, 0, encryptedDataWithTag, ciphertext.length, authTag.length);
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(SYMMETRIC_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, encryptedMsg.getIv());
            cipher.init(Cipher.DECRYPT_MODE, channel.getSymmetricKey(), gcmSpec);
            
            // Add channel ID as additional authenticated data
            cipher.updateAAD(channel.getChannelId().getBytes(StandardCharsets.UTF_8));
            
            return cipher.doFinal(encryptedDataWithTag);
            
        } catch (Exception e) {
            LOGGER.severe("Error decrypting message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates digital signature for a message
     */
    private byte[] createMessageSignature(byte[] data, SecureChannel channel) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(channel.getChannelKeyPair().getPrivate());
            signature.update(data);
            signature.update(channel.getChannelId().getBytes(StandardCharsets.UTF_8));
            
            return signature.sign();
            
        } catch (Exception e) {
            LOGGER.warning("Error creating message signature: " + e.getMessage());
            return new byte[0];
        }
    }
    
    /**
     * Verifies message signature
     */
    private boolean verifyMessageSignature(EncryptedMessage encryptedMsg, SecureChannel channel) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(channel.getBankPublicKey());
            signature.update(encryptedMsg.getEncryptedData());
            signature.update(channel.getChannelId().getBytes(StandardCharsets.UTF_8));
            
            return signature.verify(encryptedMsg.getSignature());
            
        } catch (Exception e) {
            LOGGER.warning("Error verifying message signature: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Transmits message (placeholder for actual network transmission)
     */
    private boolean transmitMessage(EncryptedMessage message, SecureChannel channel) {
        try {
            // In a real implementation, this would send over network protocols
            // For now, just simulate successful transmission
            
            pendingMessages.put(message.getMessageId(), message);
            
            LOGGER.info("Message transmitted: " + message.getMessageId());
            return true;
            
        } catch (Exception e) {
            LOGGER.warning("Error transmitting message: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Generates master key pair for the channel system
     */
    private KeyPair generateMasterKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_LENGTH);
            return generator.generateKeyPair();
            
        } catch (NoSuchAlgorithmException e) {
            LOGGER.severe("Error generating master key pair: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Closes a channel
     */
    public boolean closeChannel(String channelId) {
        try {
            SecureChannel channel = activeChannels.get(channelId);
            if (channel == null) {
                return false;
            }
            
            // Send disconnect message
            sendEncryptedMessage(channelId, MessageType.DISCONNECT, 
                               "Channel closing".getBytes(StandardCharsets.UTF_8), null);
            
            // Update channel state
            channel.setState(ChannelState.TERMINATED);
            
            // Remove from active channels
            activeChannels.remove(channelId);
            
            // Remove from bank mapping
            List<String> bankChannelList = bankChannels.get(channel.getBankId());
            if (bankChannelList != null) {
                bankChannelList.remove(channelId);
                if (bankChannelList.isEmpty()) {
                    bankChannels.remove(channel.getBankId());
                }
            }
            
            eventLog.logEvent("SECURE_CHANNEL_CLOSED: " + channelId + 
                            " (Bank: " + channel.getBankId() + ")");
            
            LOGGER.info("Channel closed: " + channelId);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error closing channel: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleanup expired channels and messages
     */
    public void cleanup() {
        try {
            // Remove expired channels
            List<String> expiredChannels = new ArrayList<>();
            for (Map.Entry<String, SecureChannel> entry : activeChannels.entrySet()) {
                SecureChannel channel = entry.getValue();
                if (channel.isExpired() || channel.getState() == ChannelState.TERMINATED) {
                    expiredChannels.add(entry.getKey());
                }
            }
            
            for (String channelId : expiredChannels) {
                closeChannel(channelId);
            }
            
            // Remove expired messages
            pendingMessages.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            LOGGER.info("Cleanup completed. Active channels: " + activeChannels.size() + 
                       ", Pending messages: " + pendingMessages.size());
            
        } catch (Exception e) {
            LOGGER.warning("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Public API methods
     */
    public SecureChannel getChannel(String channelId) {
        return activeChannels.get(channelId);
    }
    
    public List<SecureChannel> getChannelsForBank(String bankId) {
        List<String> channelIds = bankChannels.get(bankId);
        if (channelIds == null) {
            return new ArrayList<>();
        }
        
        return channelIds.stream()
            .map(activeChannels::get)
            .filter(Objects::nonNull)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public Collection<SecureChannel> getAllActiveChannels() {
        return activeChannels.values().stream()
            .filter(channel -> channel.getState() == ChannelState.ACTIVE)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalChannels", totalChannels.get());
        stats.put("activeChannels", activeChannels.size());
        stats.put("totalMessages", totalMessages.get());
        stats.put("pendingMessages", pendingMessages.size());
        
        // Channel state distribution
        Map<ChannelState, Long> stateDistribution = activeChannels.values().stream()
            .collect(Collectors.groupingBy(SecureChannel::getState, Collectors.counting()));
        stats.put("channelStates", stateDistribution);
        
        // Bank distribution
        stats.put("banksWithChannels", bankChannels.size());
        
        return stats;
    }
    
    /**
     * Gets master public key for bank integration
     */
    public PublicKey getMasterPublicKey() {
        return masterKeyPair.getPublic();
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            // Close all active channels
            for (String channelId : new ArrayList<>(activeChannels.keySet())) {
                closeChannel(channelId);
            }
            
            // Clear all data
            activeChannels.clear();
            bankChannels.clear();
            pendingMessages.clear();
            
            LOGGER.info("BankAPIEncryptedChannel shutdown completed");
            
        } catch (Exception e) {
            LOGGER.warning("Error during shutdown: " + e.getMessage());
        }
    }
}