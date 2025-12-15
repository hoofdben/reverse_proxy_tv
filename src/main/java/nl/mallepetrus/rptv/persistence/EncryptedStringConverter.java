package nl.mallepetrus.rptv.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import nl.mallepetrus.rptv.crypto.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    // JPA may instantiate converter outside Spring; handle both cases
    private static CryptoService staticCryptoService;

    @Autowired
    public void setCryptoService(CryptoService cryptoService) {
        staticCryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return staticCryptoService == null ? attribute : staticCryptoService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return staticCryptoService == null ? dbData : staticCryptoService.decrypt(dbData);
    }
}
