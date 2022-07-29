package io.octopus.broker.security;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.octopus.config.IConfig;
import io.octopus.contants.BrokerConstants;
import io.octopus.kernel.kernel.security.ssl.ISslContextCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;

/**
 * Octopus integration implementation to load SSL certificate from local filesystem path configured in
 * config file.
 * ssl 上下文创建类
 */
public class DefaultOctopusSslContextCreator implements ISslContextCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOctopusSslContextCreator.class);

    private final IConfig props;

    public DefaultOctopusSslContextCreator(IConfig props) {
        this.props = Objects.requireNonNull(props);
    }

    @Override
    public SslContext initSSLContext() {
        LOGGER.info("Checking SSL configuration properties...");

        final String keyPassword = props.getProperty(BrokerConstants.KEY_MANAGER_PASSWORD_PROPERTY_NAME);
        if (keyPassword == null || keyPassword.isEmpty()) {
            LOGGER.warn("The key manager password is null or empty. The SSL context won't be initialized.");
            return null;
        }

        try {
            SslProvider sslProvider = getSSLProvider();
            KeyStore ks = loadKeyStore();
            SslContextBuilder contextBuilder;
            switch (sslProvider) {
            case JDK:
                contextBuilder = builderWithJdkProvider(ks, keyPassword);
                break;
            case OPENSSL:
            case OPENSSL_REFCNT:
                contextBuilder = builderWithOpenSSLProvider(ks, keyPassword);
                break;
            default:
                LOGGER.error("unsupported SSL provider {}", sslProvider);
                return null;
            }
            // if client authentification is enabled a trustmanager needs to be added to the ServerContext
            String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
            if (Boolean.valueOf(sNeedsClientAuth)) {
                addClientAuthentication(ks, contextBuilder);
            }
            contextBuilder.sslProvider(sslProvider);
            SslContext sslContext = contextBuilder.build();
            LOGGER.info("The SSL context has been initialized successfully.");
            return sslContext;
        } catch (GeneralSecurityException | IOException ex) {
            LOGGER.error("Unable to initialize SSL context.", ex);
            return null;
        }
    }

    private KeyStore loadKeyStore() throws IOException, GeneralSecurityException {
        final String jksPath = props.getProperty(BrokerConstants.JKS_PATH_PROPERTY_NAME);
        LOGGER.info("Initializing SSL context. KeystorePath = {}.", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            LOGGER.warn("The keystore path is null or empty. The SSL context won't be initialized.");
            return null;
        }
        final String keyStorePassword = props.getProperty(BrokerConstants.KEY_STORE_PASSWORD_PROPERTY_NAME);
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            LOGGER.warn("The keystore password is null or empty. The SSL context won't be initialized.");
            return null;
        }
        String ksType = props.getProperty(BrokerConstants.KEY_STORE_TYPE, "JKS");
        final KeyStore keyStore = KeyStore.getInstance(ksType);
        LOGGER.info("Loading keystore. KeystorePath = {}.", jksPath);
        try (InputStream jksInputStream = jksDatastore(jksPath)) {
            keyStore.load(jksInputStream, keyStorePassword.toCharArray());
        }
        return keyStore;
    }

    private static SslContextBuilder builderWithJdkProvider(KeyStore ks, String keyPassword)
            throws GeneralSecurityException {
        LOGGER.info("Initializing key manager...");
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPassword.toCharArray());
        LOGGER.info("Initializing SSL context...");
        return SslContextBuilder.forServer(kmf);
    }

    /**
     * The OpenSSL provider does not support the {@link KeyManagerFactory}, so we have to lookup the integration
     * certificate and key in order to provide it to OpenSSL.
     * <p>
     * TODO: SNI is currently not supported, we use only the first found private key.
     */
    private static SslContextBuilder builderWithOpenSSLProvider(KeyStore ks, String keyPassword)
            throws GeneralSecurityException {
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                PrivateKey key = (PrivateKey) ks.getKey(alias, keyPassword.toCharArray());
                Certificate[] chain = ks.getCertificateChain(alias);
                X509Certificate[] certChain = new X509Certificate[chain.length];
                System.arraycopy(chain, 0, certChain, 0, chain.length);
                return SslContextBuilder.forServer(key, certChain);
            }
        }
        throw new KeyManagementException("the SSL key-store does not contain a private key");
    }

    private static void addClientAuthentication(KeyStore ks, SslContextBuilder contextBuilder)
            throws NoSuchAlgorithmException, KeyStoreException {
        LOGGER.warn("Client authentication is enabled. The keystore will be used as a truststore.");
        // use keystore as truststore, as integration needs to trust certificates signed by the integration certificates
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        contextBuilder.clientAuth(ClientAuth.REQUIRE);
        contextBuilder.trustManager(tmf);
    }

    private SslProvider getSSLProvider() {
        String providerName = props.getProperty(BrokerConstants.SSL_PROVIDER, SslProvider.JDK.name());
        try {
            return SslProvider.valueOf(providerName);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("unknown SSL Provider {}, falling back on JDK provider", providerName);
            return SslProvider.JDK;
        }
    }

    private InputStream jksDatastore(String jksPath) throws FileNotFoundException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            LOGGER.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }
        LOGGER.warn("No keystore has been found in the bundled resources. Scanning filesystem...");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            LOGGER.info("Loading external keystore. Url = {}.", jksFile.getAbsolutePath());
            return new FileInputStream(jksFile);
        }
        throw new FileNotFoundException("The keystore file does not exist. Url = " + jksFile.getAbsolutePath());
    }
}
