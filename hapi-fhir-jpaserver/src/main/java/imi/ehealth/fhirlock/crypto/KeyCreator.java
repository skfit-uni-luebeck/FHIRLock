/*
 * Decompiled with CFR 0_129.
 */
package imi.ehealth.fhirlock.crypto;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Creates a pair of Public and Private Key (RSA)
 * provides functions to load keys from and save as a base64 encoded String
 * based on https://stackoverflow.com/questions/9755057/converting-strings-to-encryption-keys-and-vice-versa-java
 */
public class KeyCreator {
    private KeyPair pair;

    /**
     * Creates a new key pair
     */
    public KeyCreator() {
        KeyPairGenerator gen = null;
        try {
            gen = KeyPairGenerator.getInstance("RSA");
            pair = gen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load a private key from a base64 encoded string
     * @param key64 the encoded string
     * @return the key
     * @throws GeneralSecurityException
     */
    public static PrivateKey loadPrivateKey(String key64) throws GeneralSecurityException {
        byte[] clear = Base64.getDecoder().decode(key64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PrivateKey priv = fact.generatePrivate(keySpec);
        Arrays.fill(clear, (byte)0);
        return priv;
    }

    /**
     * Load a public key from a base64 encoded string
     * @param stored the encoded string
     * @return the key
     * @throws GeneralSecurityException
     */
    public static PublicKey loadPublicKey(String stored) throws GeneralSecurityException {
        byte[] data = Base64.getDecoder().decode(stored);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        return fact.generatePublic(spec);
    }

    /**
     * Save a private key as a base64 encoded string
     * @param priv the Private Key
     * @return the encoded string
     * @throws GeneralSecurityException
     */
    public static String savePrivateKey(PrivateKey priv) throws GeneralSecurityException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec spec = fact.getKeySpec(priv, PKCS8EncodedKeySpec.class);
        byte[] packed = spec.getEncoded();
        String key64 = Base64.getEncoder().encodeToString(packed);
        Arrays.fill(packed, (byte)0);
        return key64;
    }

    /**
     * Save a public key as a base64 encoded String
     * @param publ the Public Key
     * @return the encoded String
     * @throws GeneralSecurityException
     */
    public static String savePublicKey(PublicKey publ) throws GeneralSecurityException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = fact.getKeySpec(publ, X509EncodedKeySpec.class);
        return Base64.getEncoder().encodeToString(spec.getEncoded());
    }

    /**
     * get the pair's public key
     * @return the public key
     */
    public PublicKey getPublicKey() {
        return pair.getPublic();
    }

    /**
     * get the pair's private key
     * @return the private key
     */
    public PrivateKey getPrivateKey() {
        return pair.getPrivate();
    }
}
