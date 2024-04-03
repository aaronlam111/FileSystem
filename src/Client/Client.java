package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 1234;
        String textPath = "src/Server/Files/text";
        String othertextPath = "src/Server/Files/othertext";

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.println("Enter request:");
                String request = input.readLine();

                byte[] buffer = request.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(packet);
                System.out.println("Request sent");

                byte[] replyBuffer = new byte[1024];
                DatagramPacket replyPacket = new DatagramPacket(replyBuffer, replyBuffer.length);
                socket.receive(replyPacket);
                String reply = new String(replyPacket.getData(), 0, replyPacket.getLength());
                System.out.println("Reply received: " + reply);
                
                
                if (reply.contains("Monitoring")){
                    int start = reply.lastIndexOf("for ") + 4;
                    int end = reply.lastIndexOf(" seconds");
                    int time = Integer.parseInt(reply.substring(start, end));
                    long endTime = System.currentTimeMillis() + time * 1000;
                    while (System.currentTimeMillis() < endTime) {
                        socket.receive(replyPacket);
                        reply = new String(replyPacket.getData(), 0, replyPacket.getLength());
                        System.out.println("Reply received: " + reply);
                        Thread.sleep(500);
                    }
                }
            }
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
