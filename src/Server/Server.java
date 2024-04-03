package Server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Server {
    private static Map<String, List<Client>> clients = new HashMap<>();
    private static Map<Client, Timer> timers = new HashMap<>();

    static class Client {
        InetAddress address;
        int port;

        public Client(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            // prepare to receive request fom clients
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            System.out.println("Server started");

            while (true) {
                //receive request from client
                socket.receive(packet);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                String request = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received: " + request);
                
                String reply = processRequest(request, clientAddress, clientPort);

                byte[] replyBuffer = reply.getBytes();
                DatagramPacket replyPacket = new DatagramPacket(replyBuffer, replyBuffer.length, clientAddress,
                        clientPort);
                socket.send(replyPacket);
                System.out.println("Reply sent to " + clientAddress + ":" + clientPort + " - " + reply);
            }
        }
    }

    private static String processRequest(String request, InetAddress address, int port) {
        //checks if the request is valid
        String[] parts = request.split(" ");
        if (parts.length != 4 && parts.length != 3 && parts.length != 2 && parts.length != 1) {
            return "Incorrect number of arguments";
        }

        String requestType = parts[0];

        //process request based on number arguments and type
        if (parts.length == 4) {
            if (requestType.equals("read")) {
                String filePath = parts[1];
                int offset = Integer.parseInt(parts[2]);
                int length = Integer.parseInt(parts[3]);
                return "File: " + readFile(filePath, offset, length);

            } else if (requestType.equals("write")) {
                String filePath = parts[1];
                int offset = Integer.parseInt(parts[2]);
                String content = parts[3];
                return writeFile(filePath, offset, content);

            } else {
                return "Invalid request type";
            }
        } else if (parts.length == 3) {
            if (requestType.equals("register")) {
                String filePath = parts[1];
                int time = Integer.parseInt(parts[2]);
                return registerClient(filePath, time, address, port);
            } else {
                return "Invalid request type";
            }
        } else if (parts.length == 2) {
            if (requestType.equals("delete")) {
                String filePath = parts[1];
                return deleteFile(filePath);
            } else {
                return "Invalid request type";
            }
        } else if (parts.length == 1) {
            if (requestType.equals("create")) {
                return createFile();
            } else {
                return "Invalid request type";
            }
        } else {
            return "Incorrect number of arguments";
        }
    }

    //method to read content of file from offset to length
    private static String readFile(String filepath, int offset, int length) {
        try {
            File file = new File(filepath);
            if (!file.exists()) {
                return "File does not exist";
            }
            if (offset >= file.length()) {
                return "Offset is greater than file length";
            }
            if (offset + length > file.length()) {
                return "Offset + length is greater than file length";
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            randomAccessFile.seek(offset);
            byte[] buffer = new byte[length];
            int bytesRead = randomAccessFile.read(buffer, 0, buffer.length);
            randomAccessFile.close();
            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead);
            } else {
                return "Unable to read file";
            }
        } catch (Exception e) {
            return "ReadFile Error: " + e.getMessage();
        }
    }

    //method to write content to file from offset
    private static String writeFile(String filepath, int offset, String length) {
        try {
            File file = new File(filepath);
            if (!file.exists()) {
                return "File does not exist";
            }
            if (offset >= file.length()) {
                return "Offset is greater than file length";
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            randomAccessFile.seek(offset);
            byte[] remaining = new byte[(int) randomAccessFile.length() - offset];
            randomAccessFile.read(remaining);
            randomAccessFile.seek(offset);
            randomAccessFile.write(length.getBytes());
            randomAccessFile.write(remaining);
            randomAccessFile.close();
            updateClients(filepath);
            return "Successfully Written!";
        } catch (Exception e) {
            return "Write Error: " + e.getMessage();
        }
    }

    //method to update clients monitoring the filepath
    private static void updateClients(String filepath) {
        List<Client> clientList = clients.get(filepath);
        File file = new File(filepath);
        if (clientList != null) {
            for (Client client : clientList) {
                try {
                    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                    randomAccessFile.seek(0);
                    byte[] buffer = new byte[(int) randomAccessFile.length()];
                    int bytesRead = randomAccessFile.read(buffer, 0, buffer.length);
                    randomAccessFile.close();
                    
                    if (bytesRead > 0) {
                        String content = new String(buffer, 0, bytesRead);
                        try (DatagramSocket socket = new DatagramSocket()) {
                            InetAddress address = client.address;
                            int port = client.port;
                            byte[] replyBuffer = content.getBytes();
                            DatagramPacket replyPacket = new DatagramPacket(replyBuffer, replyBuffer.length, address,
                                    port);
                            socket.send(replyPacket);
                            System.out.println("Update sent to " + address + ":" + port);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //method to register client to monitor specified filepath for x amount of seconds
    private static String registerClient(String filePath, int time, InetAddress address, int port) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return "File does not exist";
            }
            if (clients.containsKey(filePath)) {
                List<Client> clientList = clients.get(filePath);
                clientList.add(new Client(address, port));
            } else {
                List<Client> clientList = new ArrayList<>();
                clientList.add(new Client(address, port));
                clients.put(filePath, clientList);
            }
            
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    clients.get(filePath).removeIf(client -> client.address.equals(address) && client.port == port);
                    timers.remove(new Client(address, port));
                    System.out.println("Client removed: " + address + ":" + port);
                }
            }, time * 1000);
            timers.put(new Client(address, port), timer);

            return "Monitoring filepath: " + filePath + " for " + time + " seconds";
        } catch (Exception e) {
            return "Register Error: " + e.getMessage();
        }
    }

    //method to create a file using current timestamp
    private static String createFile() {
        String directory = "src/Server/Files/";
        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = date.format(new Date(System.currentTimeMillis()));
        String fileName = timestamp + ".txt";
        String filePath = directory + fileName;
        try {
            File file = new File(filePath);
            if (file.createNewFile()) {
                return "File created: " + filePath;
            } else {
                return "File already exists";
            }
        } catch (IOException e) {
            return "Create Error: " + e.getMessage();
        }
    }

    //method to delete file at specified filepath
    private static String deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return "File does not exist";
            }
            file.delete();
            return "File Path: " + filePath + " has been deleted";
        } catch (Exception e) {
            return "Clear Error: " + e.getMessage();
        }
    }
}
