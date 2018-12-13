package SecureMessaging;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.net.InetSocketAddress;

public class SecureEndpoint extends Endpoint {
    private static final String ALGORTITHM_AES = "AES";
    Cipher encryptor;
    Cipher decryptor;
    SecretKeySpec secretKeySpec;
    private final Endpoint endpoint;

    public SecureEndpoint() {
        init();
        endpoint = new Endpoint();
    }

    public SecureEndpoint(int port) {
        init();
        endpoint = new Endpoint(port);
    }

    private void init() {
        secretKeySpec = new SecretKeySpec("CAFEBABECAFEBABE".getBytes(),ALGORTITHM_AES);
        try{
            encryptor = Cipher.getInstance(ALGORTITHM_AES);
            encryptor.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            decryptor = Cipher.getInstance(ALGORTITHM_AES);
            decryptor.init(Cipher.DECRYPT_MODE, secretKeySpec);
        } catch(Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        payload = encrypt(payload);
        endpoint.send(receiver, payload);
    }

    public Message blockingReceive() {
        Message message = endpoint.blockingReceive();
        Serializable payload = decrypt((SealedObject) message.getPayload());
        return new Message(payload, message.getSender());
    }

    public Message nonBlockingReceive() {
        Message message = endpoint.nonBlockingReceive();
        Serializable payload = decrypt((SealedObject) message.getPayload());
        return new Message(payload, message.getSender());
    }

    private Serializable encrypt(Serializable payload) {
        SealedObject sealedObject;
        try {
             sealedObject = new SealedObject(payload, encryptor);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            sealedObject = null;
        }
        return sealedObject;
    }

    private Serializable decrypt(SealedObject sealedObject) {
        Serializable payload;
        try {
            payload = (Serializable) sealedObject.getObject(decryptor);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            payload = null;
        }
        return payload;
    }

}
