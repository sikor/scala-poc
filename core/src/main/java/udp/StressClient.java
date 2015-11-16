package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

class StressClient {
    public static void main(String args[]) throws Exception {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("localhost");
        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];
        String sentence = "dupa";
        sendData = sentence.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        long start = System.currentTimeMillis();
        float time = 100;

        clientSocket.setSoTimeout(1000);
        clientSocket.setSendBufferSize(66000 * 100);
        clientSocket.setReceiveBufferSize(66000 * 100);

        System.out.println("receive buff size: " + clientSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + clientSocket.getSendBufferSize());
        Thread t = new Thread(() -> {
            try {
                receive(clientSocket, receivePacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                send(clientSocket, sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
        t2.start();
        t.join();
        t2.join();
        clientSocket.close();
    }

    private static void receive(DatagramSocket clientSocket, DatagramPacket receivePacket) {
        long counter = 0;
        long start = System.currentTimeMillis();
        float time = 20;

        try {
            while ((System.currentTimeMillis() - start) / 1000 < time) {
                ++counter;
                clientSocket.receive(receivePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("received: " + counter / time + " " + counter);

    }

    private static void send(DatagramSocket clientSocket, DatagramPacket sendPacket) throws IOException {
        long counter = 0;
        long start = System.currentTimeMillis();
        float time = 20;

        while ((System.currentTimeMillis() - start) / 1000 < time) {
            ++counter;
            clientSocket.send(sendPacket);
        }

        System.out.println("sent: " + counter / time + " " + counter);

    }
}