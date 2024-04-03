package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class Client {
    private static final Map<String, byte[]> cache = new HashMap<>();
    private static final Map<String, Long> cacheTime = new HashMap<>();
    private static final int FRESHNESS = 60;
    public static void main(String[] args) {
        String host = "localhost";
        int port = 1234;
        String textPath = "src/Server/Files/text";

        try (DatagramSocket socket = new DatagramSocket()) {
            //get the address of the server
            InetAddress address = InetAddress.getByName(host);
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            //send requests to the server until exit is entered
            while (true) {
                System.out.println("Enter request:");
                String request = input.readLine();

                //check if the request is to exit or if it is a read request
                String[] requestParts = request.split(" ");
                if (requestParts[0].equals("exit")) {
                    break;
                } else if (requestParts[0].equals("read") && requestParts.length == 4) {
                    if (cache.containsKey(request) && isCacheFresh(request)) {
                        byte[] file = cache.get(request);
                        System.out.println("File found in cache");
                        System.out.println("Cache: " + new String(file));
                        continue;
                    }
                }
                //send the request to the server
                byte[] buffer = request.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(packet);
                System.out.println("Request sent");

                //receive the reply from the server
                byte[] replyBuffer = new byte[1024];
                DatagramPacket replyPacket = new DatagramPacket(replyBuffer, replyBuffer.length);
                socket.receive(replyPacket);
                String reply = new String(replyPacket.getData(), 0, replyPacket.getLength());
                System.out.println(reply);

                //check if the request is a read request and save the file to cache
                if (reply.contains("File:")) {
                    String[] replyParts = reply.split(": ");
                    byte[] file = replyParts[1].getBytes();
                    cache.put(request, file);
                    cacheTime.put(request, System.currentTimeMillis() / 1000);
                    System.out.println("Content saved to cache");
                }

                //check if the request is monitoring a file
                if (reply.contains("Monitoring")){
                    int start = reply.lastIndexOf("for ") + 4;
                    int end = reply.lastIndexOf(" seconds");
                    int time = Integer.parseInt(reply.substring(start, end));
                    long endTime = System.currentTimeMillis() + time * 1000;
                    //receive updates until the time is up
                    while (System.currentTimeMillis() < endTime) {
                        socket.setSoTimeout(time * 1000);
                        try {
                            socket.receive(replyPacket);
                            reply = new String(replyPacket.getData(), 0, replyPacket.getLength());
                            System.out.println("Update received: " + reply);
                        } catch (SocketTimeoutException e) {
                            System.out.println("All replies received");
                            continue;
                        }
                    }
                }
            }
            System.out.println("Client closed");
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }

    private static boolean isCacheFresh(String filepath) {
        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - cacheTime.get(filepath)) < FRESHNESS;
    }
}
