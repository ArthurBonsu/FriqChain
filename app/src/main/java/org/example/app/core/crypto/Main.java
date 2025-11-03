package org.example.app.core.crypto;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

public class Main {
    public static void main(String[] args) {
        try {
            // Step 1: Certificate Authority Initialization
            System.out.println("Initializing Certificate Authority...");
            CertificateAuthority ca = new CertificateAuthority();

            // Step 2: Node A - Generate Keypair and Request Certificate
            System.out.println("\nNode A: Generating keys and requesting certificate...");
            PKIManager nodeA = new PKIManager();
            nodeA.requestCertificate(ca);

            // Step 3: Node B - Generate Keypair and Request Certificate
            System.out.println("\nNode B: Generating keys and requesting certificate...");
            PKIManager nodeB = new PKIManager();
            nodeB.requestCertificate(ca);

            // Step 4: Verify Certificates
            System.out.println("\nVerifying certificates...");
            boolean isNodeAValid = ca.verifyCertificate(nodeA.certificate);
            boolean isNodeBValid = ca.verifyCertificate(nodeB.certificate);
            System.out.println("Node A certificate valid: " + isNodeAValid);
            System.out.println("Node B certificate valid: " + isNodeBValid);

            // Step 5: Diffie-Hellman Key Exchange
            System.out.println("\nPerforming Diffie-Hellman Key Exchange...");
            DiffieHellmanKeyExchange dhA = new DiffieHellmanKeyExchange();
            DiffieHellmanKeyExchange dhB = new DiffieHellmanKeyExchange();

            byte[] publicKeyA = dhA.getPublicKey();
            byte[] publicKeyB = dhB.getPublicKey();

            SecretKey sharedKeyA = dhA.generateSharedKey(publicKeyB);
            SecretKey sharedKeyB = dhB.generateSharedKey(publicKeyA);

            System.out.println("Shared keys match: " + sharedKeyA.equals(sharedKeyB));

            // Step 6: Encrypt and Decrypt Data with Shared Symmetric Key
            System.out.println("\nEncrypting and decrypting data...");
            String message = "Hello, secure world!";
            String encryptedMessage = SymmetricKeyManager.encrypt(message, sharedKeyA);
            String decryptedMessage = SymmetricKeyManager.decrypt(encryptedMessage, sharedKeyB);

            System.out.println("Original Message: " + message);
            System.out.println("Encrypted Message: " + encryptedMessage);
            System.out.println("Decrypted Message: " + decryptedMessage);

            // Step 7: Signing Data
            System.out.println("\nNode A signing data...");
            byte[] data = "Data to sign".getBytes();
            Keypair keypairA = nodeA.keypair;

            Keypair.SignatureResult signature = keypairA.sign(data);
            boolean isSignatureValid = keypairA.verify(signature, data);
            System.out.println("Signature valid: " + isSignatureValid);

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        try {
            PKIManager.setDatabaseFile("keys.db");
            System.out.println("Testing PKIManager with SQLite...");

            // Step 1: Generate a new PKIManager with a keypair
            System.out.println("\nGenerating a new key pair...");
            PKIManager manager = new PKIManager();
            String nodeId = "test_node";

            // Step 2: Store the key pair in the SQLite database
            System.out.println("\nStoring the key pair in the database...");
            manager.storeKeypair(nodeId);

            // Step 3: Retrieve the key pair from the database
            System.out.println("\nRetrieving the key pair from the database...");
            Keypair retrievedKeypair = PKIManager.loadKeypair(nodeId);

            // Step 4: Verify that the retrieved keys have expected properties
            System.out.println("\nVerifying the retrieved key pair...");
            boolean hasPublicKey = retrievedKeypair.getPublicKey() != null;
            boolean hasPrivateKey = retrievedKeypair.getPrivateKey() != null;

            System.out.println("Has public key: " + hasPublicKey);
            System.out.println("Has private key: " + hasPrivateKey);

            if (hasPublicKey && hasPrivateKey) {
                System.out.println("\nKey pair storage and retrieval successful!");
            } else {
                System.out.println("\nKey pair storage or retrieval failed.");
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}