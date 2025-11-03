package org.example.app.core.crypto;

import java.security.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public class CertificateAuthority {
    private static final Logger LOGGER = Logger.getLogger(CertificateAuthority.class.getName());
    
    private final Keypair caKeypair;
    private final String signatureAlgorithm;
    private final CertificateDataBuilder dataBuilder;

    public static class CertificateDataBuilder {
        private boolean includeTimestamp = true;
        private boolean includeSerialNumber = true;
        private long validityPeriod = 365 * 24 * 60 * 60 * 1000L; // 1 year in milliseconds

        public CertificateDataBuilder withTimestamp(boolean include) {
            this.includeTimestamp = include;
            return this;
        }

        public CertificateDataBuilder withSerialNumber(boolean include) {
            this.includeSerialNumber = include;
            return this;
        }

        public CertificateDataBuilder withValidityPeriod(long periodInMillis) {
            this.validityPeriod = periodInMillis;
            return this;
        }

        public byte[] buildCertificateData(String nodeId, PublicKey publicKey) {
            Objects.requireNonNull(publicKey, "Public key cannot be null");
            
            try {
                // Start with node ID (if present)
                byte[] nodeIdBytes = nodeId != null ? nodeId.getBytes() : new byte[0];
                byte[] publicKeyBytes = publicKey.getEncoded();
                
                // Calculate total length
                int totalLength = nodeIdBytes.length + publicKeyBytes.length;
                
                if (includeTimestamp) {
                    totalLength += 8; // long = 8 bytes
                }
                if (includeSerialNumber) {
                    totalLength += 8; // long = 8 bytes
                }

                byte[] combined = new byte[totalLength];
                int offset = 0;

                // Combine all components
                System.arraycopy(nodeIdBytes, 0, combined, offset, nodeIdBytes.length);
                offset += nodeIdBytes.length;

                System.arraycopy(publicKeyBytes, 0, combined, offset, publicKeyBytes.length);
                offset += publicKeyBytes.length;

                if (includeTimestamp) {
                    byte[] timestamp = longToBytes(System.currentTimeMillis());
                    System.arraycopy(timestamp, 0, combined, offset, 8);
                    offset += 8;
                }

                if (includeSerialNumber) {
                    byte[] serial = longToBytes(generateSerialNumber());
                    System.arraycopy(serial, 0, combined, offset, 8);
                }

                return combined;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate certificate data", e);
            }
        }

        private byte[] longToBytes(long value) {
            byte[] result = new byte[8];
            for (int i = 7; i >= 0; i--) {
                result[i] = (byte)(value & 0xFFL);
                value >>= 8;
            }
            return result;
        }

        private long generateSerialNumber() {
            return SecureRandom.getSeed(8)[0] & System.nanoTime();
        }
    }

    public CertificateAuthority() {
        this(Keypair.generate(), "SHA256withECDSA", new CertificateDataBuilder());
    }

    public CertificateAuthority(Keypair caKeypair, String signatureAlgorithm, CertificateDataBuilder dataBuilder) {
        this.caKeypair = Objects.requireNonNull(caKeypair, "CA keypair cannot be null");
        this.signatureAlgorithm = Objects.requireNonNull(signatureAlgorithm, "Signature algorithm cannot be null");
        this.dataBuilder = Objects.requireNonNull(dataBuilder, "Data builder cannot be null");
    }

    public Certificate issueCertificate(String nodeId, PublicKey publicKey) throws GeneralSecurityException {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }

        try {
            byte[] dataToSign = dataBuilder.buildCertificateData(nodeId, publicKey);
            
            Signature signature = Signature.getInstance(signatureAlgorithm);
            signature.initSign(caKeypair.getPrivateKey());
            signature.update(dataToSign);
            
            byte[] signatureBytes = signature.sign();
            
            LOGGER.fine(() -> String.format("Issued certificate for nodeId: %s", nodeId));
            return new Certificate(nodeId, publicKey, signatureBytes);
        } catch (GeneralSecurityException e) {
            LOGGER.severe(() -> String.format("Failed to issue certificate for nodeId: %s, error: %s", 
                         nodeId, e.getMessage()));
            throw e;
        }
    }

    public boolean verifyCertificate(Certificate certificate) {
        if (certificate == null) {
            LOGGER.warning("Attempted to verify null certificate");
            return false;
        }

        try {
            byte[] dataToVerify = dataBuilder.buildCertificateData(
                certificate.getNodeId(), 
                certificate.getPublicKey()
            );
            
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(caKeypair.getPublicKey());
            verifier.update(dataToVerify);
            
            boolean isValid = verifier.verify(certificate.getSignature());
            LOGGER.fine(() -> String.format("Certificate verification result for nodeId %s: %b", 
                       certificate.getNodeId(), isValid));
            
            return isValid;
        } catch (GeneralSecurityException | RuntimeException e) {
            LOGGER.severe(() -> String.format("Certificate verification failed for nodeId: %s, error: %s",
                         certificate.getNodeId(), e.getMessage()));
            return false;
        }
    }

    public PublicKey getPublicKey() {
        return caKeypair.getPublicKey();
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }
}