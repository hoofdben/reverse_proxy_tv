package nl.mallepetrus.rptv.crypto;

public interface CryptoService {
    String encrypt(String plainText);
    String decrypt(String cipherText);
}
