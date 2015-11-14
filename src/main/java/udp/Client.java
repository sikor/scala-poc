package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.*;
import java.net.*;

class Client {
    public static void main(String args[]) throws Exception {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];
        int counter = 0;
        String sentence = "dupa";
        sendData = sentence.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        long start = System.currentTimeMillis();
        float time = 100;

        clientSocket.setSoTimeout(10000);
        while ((System.currentTimeMillis() - start) / 1000 < time) {
            ++counter;
            send(clientSocket, sendPacket);
            receive(clientSocket, receivePacket);
        }

        clientSocket.close();
        System.out.println(counter / time);
    }

    private static void receive(DatagramSocket clientSocket, DatagramPacket receivePacket) throws IOException {
        clientSocket.receive(receivePacket);
    }

    private static void send(DatagramSocket clientSocket, DatagramPacket sendPacket) throws IOException {
        clientSocket.send(sendPacket);
    }
}