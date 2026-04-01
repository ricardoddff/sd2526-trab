package sd2526.trab.server;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Discovery {
    static final public InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 9000);
    static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
    static final int MAX_DATAGRAM_SIZE = 65536;
    private static final String DELIMITER = "\t";

    private final InetSocketAddress addr;
    private final String serviceName;
    private final String domain;
    private final String serviceURI;
    private final MulticastSocket ms;

    // Agora guarda o URI e o momento (timestamp) em que o anúncio foi recebido
    private final Map<String, Map<URI, Long>> discoveredServices = new ConcurrentHashMap<>();

    public Discovery(InetSocketAddress addr, String serviceName, String domain, String serviceURI) throws IOException {
        this.addr = addr;
        this.serviceName = serviceName;
        this.domain = domain;
        this.serviceURI = serviceURI;
        this.ms = new MulticastSocket(addr.getPort());

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                try {
                    ms.joinGroup(addr, ni);
                } catch (Exception ignored) {}
            }
        }
    }

    public void start() {
        if (serviceName != null && domain != null && serviceURI != null && !serviceURI.isEmpty()) {
            // Formato exigido: ServiceName@domain\tURI
            String announcement = String.format("%s@%s%s%s", serviceName, domain, DELIMITER, serviceURI);
            byte[] announceBytes = announcement.getBytes();
            DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

            new Thread(() -> {
                for (;;) {
                    try {
                        ms.send(announcePkt);
                        Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
                    } catch (Exception e) { }
                }
            }).start();
        }

        new Thread(() -> {
            DatagramPacket pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
            for (;;) {
                try {
                    pkt.setLength(MAX_DATAGRAM_SIZE);
                    ms.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength());
                    String[] msgElems = msg.split(DELIMITER);

                    if (msgElems.length == 2) {
                        String serviceAndDomain = msgElems[0]; // ex: Users@fct
                        URI uri = URI.create(msgElems[1].trim());

                        // Guarda/atualiza o URI com o timestamp atual
                        discoveredServices
                                .computeIfAbsent(serviceAndDomain, k -> new ConcurrentHashMap<>())
                                .put(uri, System.currentTimeMillis());
                    }
                } catch (IOException e) { }
            }
        }).start();
    }

    /**
     * Devolve os URIs de um serviço num determinado domínio que deram sinal de vida nos últimos 3 segundos.
     */
    public URI[] knownUrisOf(String serviceName, String targetDomain) {
        String key = serviceName + "@" + targetDomain;
        Map<URI, Long> urisWithTimestamps = discoveredServices.get(key);

        if (urisWithTimestamps == null || urisWithTimestamps.isEmpty()) {
            return new URI[0];
        }

        long now = System.currentTimeMillis();
        return urisWithTimestamps.entrySet().stream()
                .filter(entry -> (now - entry.getValue()) < 3000) // Ignora servidores que não anunciam há mais de 3s
                .map(Map.Entry::getKey)
                .toArray(URI[]::new);
    }
}