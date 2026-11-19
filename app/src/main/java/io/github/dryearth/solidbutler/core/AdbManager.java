package io.github.dryearth.solidbutler.core;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

/**
 * Wireless ADB backend. It binds to the device's own adbd through Android's
 * Wireless debugging pairing flow. The app never needs a PC once paired.
 */
public final class AdbManager extends AbsAdbConnectionManager {
    private static volatile AdbManager INSTANCE;
    private static volatile String LAST_ERROR = "";

    private final PrivateKey privateKey;
    private final Certificate certificate;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private AdbManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setHostAddress("127.0.0.1");
        PrivateKey loadedKey = readPrivateKey(context);
        Certificate loadedCertificate = readCertificate(context);
        if (loadedKey == null || loadedCertificate == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair pair = generator.generateKeyPair();
            PublicKey publicKey = pair.getPublic();
            PrivateKey generatedKey = pair.getPrivate();

            String subject = "CN=Solid Butler";
            String algorithm = "SHA512withRSA";
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + 10L * 365L * 24L * 60L * 60L * 1000L);
            CertificateExtensions extensions = new CertificateExtensions();
            extensions.set("SubjectKeyIdentifier",
                    new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
            X500Name name = new X500Name(subject);
            extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber",
                    new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(publicKey));
            info.set("validity", new CertificateValidity(notBefore, notAfter));
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", extensions);
            X509CertImpl impl = new X509CertImpl(info);
            impl.sign(generatedKey, algorithm);

            writePrivateKey(context, generatedKey);
            writeCertificate(context, impl);
            loadedKey = generatedKey;
            loadedCertificate = impl;
        }
        privateKey = loadedKey;
        certificate = loadedCertificate;
    }

    public static synchronized boolean init(@NonNull Context context) {
        if (INSTANCE != null) return true;
        try {
            INSTANCE = new AdbManager(context.getApplicationContext());
            LAST_ERROR = "";
            return true;
        } catch (Exception e) {
            LAST_ERROR = message(e);
            return false;
        }
    }

    public static boolean ready() {
        return INSTANCE != null;
    }

    public static boolean connected() {
        AdbManager manager = INSTANCE;
        return manager != null && manager.isConnected();
    }

    public static String lastError() {
        return LAST_ERROR;
    }

    /** Pair with the address/code shown in Wireless debugging. Returns null on success. */
    @Nullable
    public static String pairDevice(@NonNull Context context, String host, int port, String code) {
        if (!init(context)) return LAST_ERROR;
        try {
            boolean ok = INSTANCE.pair(host, port, code);
            return ok ? null : "配对没有成功";
        } catch (Exception e) {
            return message(e);
        }
    }

    /** Discover the connect port over mDNS and connect. Returns null on success. */
    @Nullable
    public static String connectDevice(@NonNull Context context, long timeoutMs) {
        if (!init(context)) return LAST_ERROR;
        try {
            boolean ok = INSTANCE.connectTls(context.getApplicationContext(), timeoutMs);
            return ok ? null : "没有找到可连接的无线调试端口";
        } catch (AdbPairingRequiredException e) {
            return "还没有完成配对：请先在无线调试里使用配对码配对";
        } catch (Exception e) {
            return message(e);
        }
    }

    /** Connect directly to the port shown on the Wireless debugging page. */
    @Nullable
    public static String connectDirect(@NonNull Context context, String host, int port) {
        if (!init(context)) return LAST_ERROR;
        try {
            boolean ok = INSTANCE.connect(host, port);
            return ok ? null : "没有连上这个端口";
        } catch (AdbPairingRequiredException e) {
            return "还没有完成配对：请先在无线调试里使用配对码配对";
        } catch (Exception e) {
            return message(e);
        }
    }

    public static void disconnectDevice() {
        AdbManager manager = INSTANCE;
        if (manager == null) return;
        try {
            manager.disconnect();
        } catch (Exception ignored) {
        }
    }

    public static final class Result {
        public final int code;
        public final String output;

        Result(int code, String output) {
            this.code = code;
            this.output = output;
        }
    }

    /** Execute a shell command through adbd. Returns null when not connected. */
    @Nullable
    public static Result run(String command, long timeoutMs) {
        AdbManager manager = INSTANCE;
        if (manager == null || !manager.isConnected()) return null;
        final AdbStream[] holder = new AdbStream[1];
        Future<Result> future = manager.executor.submit(() -> {
            AdbStream stream = manager.openStream(
                    "shell:" + command + " 2>&1; echo __XI_EXIT:$?");
            holder[0] = stream;
            try (InputStream input = stream.openInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                String raw = output.toString("UTF-8");
                int marker = raw.lastIndexOf("__XI_EXIT:");
                int code = 0;
                String text = raw;
                if (marker >= 0) {
                    String tail = raw.substring(marker + "__XI_EXIT:".length()).trim();
                    int end = 0;
                    while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
                        end++;
                    }
                    if (end > 0) {
                        code = Integer.parseInt(tail.substring(0, end));
                    }
                    text = raw.substring(0, marker).trim();
                }
                return new Result(code, text);
            } finally {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            close(holder[0]);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void close(AdbStream stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static String message(Exception e) {
        String text = e.getMessage();
        return text == null || text.trim().isEmpty() ? e.toString() : text;
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "Xi Wireless ADB";
    }

    @Nullable
    private static Certificate readCertificate(Context context) {
        File file = new File(context.getFilesDir(), "adb_cert.pem");
        if (!file.exists()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCertificate(Context context, Certificate certificate) throws Exception {
        File file = new File(context.getFilesDir(), "adb_cert.pem");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP)
                + "\n-----END CERTIFICATE-----\n";
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(pem.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nullable
    private static PrivateKey readPrivateKey(Context context) {
        File file = new File(context.getFilesDir(), "adb_private.key");
        if (!file.exists()) return null;
        try (InputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static void writePrivateKey(Context context, PrivateKey key) throws Exception {
        File file = new File(context.getFilesDir(), "adb_private.key");
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(key.getEncoded());
        }
    }
}
